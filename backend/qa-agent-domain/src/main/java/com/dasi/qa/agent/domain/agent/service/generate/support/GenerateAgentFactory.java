package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DecideResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.GenerateContext;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AbortAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DecideAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DraftAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlanAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SummarizeAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
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
                .name(GeneratePhase.GENERATE.getAgentName())
                .description(GeneratePhase.GENERATE.getAgentDesc())
                .subAgents(
                        decideAction,
                        routeAgent
                )
                .build();
    }

    private UntypedAgent makeRouteAgent(GenerateContext context, AgenticServices.AgenticScopeAction abortAction, UntypedAgent createAgent) {
        return AgenticServices.conditionalBuilder()
                .name(GeneratePhase.ROUTE.getAgentName())
                .description(GeneratePhase.ROUTE.getAgentDesc())
                .subAgents(scope -> DecideResult.fromScope(scope).isValid(), createAgent)
                .subAgents(scope -> !DecideResult.fromScope(scope).isValid(), abortAction)
                .build();
    }

    private UntypedAgent makeCreateAgent(AgenticServices.AgenticScopeAction planAction,
                                         AgenticServices.AgenticScopeAction writeAction,
                                         AgenticServices.AgenticScopeAction validateAction,
                                         AgenticServices.AgenticScopeAction summarizeAction) {
        return AgenticServices.sequenceBuilder()
                .name(GeneratePhase.WRITE.getAgentName())
                .description(GeneratePhase.WRITE.getAgentDesc())
                .subAgents(
                        planAction,
                        writeAction,
                        validateAction,
                        summarizeAction
                )
                .build();
    }

    public DecideAgent makeDecideAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(DecideAgent.class)
                .name(GeneratePhase.DECIDE.getAgentName())
                .description(GeneratePhase.DECIDE.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

    public AbortAgent makeAbortAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(AbortAgent.class)
                .name(GeneratePhase.ABORT.getAgentName())
                .description(GeneratePhase.ABORT.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();
    }

    public PlanAgent makePlanAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener, List<Object> writeTools) {
        return AgenticServices.agentBuilder(PlanAgent.class)
                .name(GeneratePhase.PLAN.getAgentName())
                .description(GeneratePhase.PLAN.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .tools(writeTools.toArray())
                .build();
    }

    public DraftAgent makeDraftAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener, List<Object> writeTools) {
        return AgenticServices.agentBuilder(DraftAgent.class)
                .name(GeneratePhase.DRAFT.getAgentName())
                .description(GeneratePhase.DRAFT.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .tools(writeTools.toArray())
                .build();
    }

    public EvaluateAgent makeEvaluateAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(EvaluateAgent.class)
                .name(GeneratePhase.EVALUATE.getAgentName())
                .description(GeneratePhase.EVALUATE.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

    public AmendAgent makeAmendAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener, List<Object> validateTools) {
        return AgenticServices.agentBuilder(AmendAgent.class)
                .name(GeneratePhase.AMEND.getAgentName())
                .description(GeneratePhase.AMEND.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .tools(validateTools.toArray())
                .build();
    }

    public SummarizeAgent makeSummarizeAgent(ChatModel userModel, ChatMemoryProvider chatMemoryProvider, AgentListener agentListener) {
        return AgenticServices.agentBuilder(SummarizeAgent.class)
                .name(GeneratePhase.SUMMARIZE.getAgentName())
                .description(GeneratePhase.SUMMARIZE.getAgentDesc())
                .chatModel(userModel)
                .chatMemoryProvider(chatMemoryProvider)
                .listener(agentListener)
                .build();
    }

}
