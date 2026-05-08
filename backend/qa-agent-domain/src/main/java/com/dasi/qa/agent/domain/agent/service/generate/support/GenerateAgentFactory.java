package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DecideResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.GenerateContext;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AbortAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DecideAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DraftAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlanAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SummarizeAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 为单次问答集生成任务构建 Generate DAG
 *  [Start]
 *    |
 *    v
 * +--------+            +-------+
 * | Decide | ---------> | Abort |
 * +--------+            +-------+
 *    |
 *    v
 * +------+
 * | Plan |
 * +------+
 *    |
 *    v
 * +--------------------------+
 * |     Write（Parallel）     |
 * |  +--------------------+  |
 * |  |      Draft-1       |  |
 * |  +--------------------+  |
 * |  |      Draft-2       |  |
 * |  +--------------------+  |
 * +--------------------------+
 *    |
 *    v
 * +------------------------------------------+
 * |                 Validate                 |
 * |  +------------------------------------+  |
 * |  |   Evaluate -> Amend -> Evaluate    |  |
 * |  +------------------------------------+  |
 * +------------------------------------------+
 *    |
 *    v
 * +-----------+
 * | Summarize |
 * +-----------+
 *    |
 *    v
 *  [End]
 * ```
 */
@Component
public class GenerateAgentFactory {

    // 组装整个 DAG 链路
    public UntypedAgent build(GenerateContext context) {
        // 1. 基于上下文构建各阶段 Agent 实例
        DecideAgent decideAgent = makeDecideAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener());
        AbortAgent abortAgent = makeAbortAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener());
        PlanAgent planAgent = makePlanAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener(), context.getWriteTools());
        DraftAgent draftAgent = makeDraftAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener(), context.getWriteTools());
        EvaluateAgent evaluateAgent = makeEvaluateAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener());
        AmendAgent amendAgent = makeAmendAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener(), context.getValidateTools());
        SummarizeAgent summarizeAgent = makeSummarizeAgent(context.getUserModel(), context.getChatMemoryProvider(), context.getAgentListener());

        // 2. 将阶段执行函数封装为 DAG 可执行节点
        AgenticServices.AgenticScopeAction decideAction =
                AgenticServices.agentAction(scope -> context.getDecideStep().run(scope, decideAgent));
        AgenticServices.AgenticScopeAction abortAction =
                AgenticServices.agentAction(scope -> context.getAbortStep().run(scope, abortAgent));
        AgenticServices.AgenticScopeAction planAction =
                AgenticServices.agentAction(scope -> context.getPlanStep().run(scope, planAgent));
        AgenticServices.AgenticScopeAction writeAction =
                AgenticServices.agentAction(scope -> context.getWriteStep().run(scope, draftAgent));
        AgenticServices.AgenticScopeAction validateAction =
                AgenticServices.agentAction(scope -> context.getValidateStep().run(scope, evaluateAgent, amendAgent));
        AgenticServices.AgenticScopeAction summarizeAction =
                AgenticServices.agentAction(scope -> context.getSummarizeStep().run(scope, summarizeAgent));

        // 3. 构造最外层工作流
        UntypedAgent createAgent = makeCreateAgent(planAction, writeAction, validateAction, summarizeAction);
        UntypedAgent routeAgent = makeRouteAgent(context, abortAction, createAgent);

        // 4. 组装并返回顶层 DAG
        return AgenticServices.sequenceBuilder()
                .name("GenerateAgent")
                .description("先执行请求判定，再根据路由结果进入终止分支或完整生成链路。")
                .subAgents(
                        decideAction,
                        routeAgent
                )
                .output(scope -> scope.readState("qaSetId"))
                .build();
    }

    private UntypedAgent makeRouteAgent(GenerateContext context, AgenticServices.AgenticScopeAction abortAction, UntypedAgent createAgent) {
        return AgenticServices.conditionalBuilder()
                .name("RouteAgent")
                .description("读取判定结果并路由到终止分支或继续执行生成分支。")
                .subAgents(scope -> DecideResult.fromScope(scope).isValid(), createAgent)
                .subAgents(scope -> !DecideResult.fromScope(scope).isValid(), abortAction)
                .listener(context.getAgentListener())
                .build();
    }

    private UntypedAgent makeCreateAgent(AgenticServices.AgenticScopeAction planAction,
                                         AgenticServices.AgenticScopeAction writeAction,
                                         AgenticServices.AgenticScopeAction validateAction,
                                         AgenticServices.AgenticScopeAction summarizeAction) {
        return AgenticServices.sequenceBuilder()
                .name("CreateAgent")
                .description("顺序执行规划、起草、校验修订与总结，生成最终可落库问答集。")
                .subAgents(
                        planAction,
                        writeAction,
                        validateAction,
                        summarizeAction
                )
                .output(scope -> scope.readState("qaSetId"))
                .build();
    }

    public DecideAgent makeDecideAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(DecideAgent.class)
                .name("DecideAgent")
                .description("识别用户请求是否满足问答集生成场景并给出判定结果。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

    public AbortAgent makeAbortAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(AbortAgent.class)
                .name("AbortAgent")
                .description("根据拒绝原因生成终止消息并结束当前生成任务。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

    public PlanAgent makePlanAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener, List<Object> writeTools) {
        AgentBuilder<PlanAgent, ?> builder = AgenticServices.agentBuilder(PlanAgent.class)
                .name("PlanAgent")
                .description("分析资料摘要并输出模块化题量与难度分配计划。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener);
        builder.tools(writeTools.toArray());
        return builder.build();
    }

    public DraftAgent makeDraftAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener, List<Object> writeTools) {
        AgentBuilder<DraftAgent, ?> builder = AgenticServices.agentBuilder(DraftAgent.class)
                .name("DraftAgent")
                .description("基于检索证据按模块起草结构化问答题目。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener);
        builder.tools(writeTools.toArray());
        return builder.build();
    }

    public EvaluateAgent makeEvaluateAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(EvaluateAgent.class)
                .name("EvaluateAgent")
                .description("审校题目准确性、完整性与证据边界并输出判定。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

    public AmendAgent makeAmendAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener, List<Object> validateTools) {
        AgentBuilder<AmendAgent, ?> builder = AgenticServices.agentBuilder(AmendAgent.class)
                .name("AmendAgent")
                .description("按审校建议进行最小必要修订并保持题目结构稳定。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener);
        builder.tools(validateTools.toArray());
        return builder.build();
    }

    public SummarizeAgent makeSummarizeAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(SummarizeAgent.class)
                .name("SummarizeAgent")
                .description("汇总生成结果与统计信息并输出最终完成说明。")
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

}
