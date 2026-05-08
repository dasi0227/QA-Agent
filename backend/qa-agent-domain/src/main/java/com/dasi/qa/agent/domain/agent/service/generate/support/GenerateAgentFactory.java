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
import dev.langchain4j.agentic.scope.AgenticScope;
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

        // 先构造每个独立的 Agent
        DecideAgent decideAgent = makeDecideAgent(context);
        AbortAgent abortAgent = makeAbortAgent(context);
        PlanAgent planAgent = makePlanAgent(context);
        DraftAgent draftAgent = makeDraftAgent(context);
        EvaluateAgent evaluateAgent = makeEvaluateAgent(context);
        AmendAgent amendAgent = makeAmendAgent(context);
        SummarizeAgent summarizeAgent = makeSummarizeAgent(context);

        // 再用 Agent 组装工作流
        UntypedAgent createAgent = makeCreateAgent(context, planAgent, draftAgent, evaluateAgent, amendAgent, summarizeAgent);
        UntypedAgent routeAgent = makeRouteAgent(context, abortAgent, createAgent);

        // 最后将工作流合并为 DAG
        UntypedAgent generateAgent = AgenticServices.sequenceBuilder()
                .name("GenerateAgent")
                .description("先执行请求判定，再根据路由结果进入终止分支或完整生成链路。")
                .subAgents(
                        AgenticServices.agentAction(scope -> context.getDecideStep().run(scope, decideAgent)),
                        routeAgent
                )
                .output(scope -> scope.readState("qaSetId"))
                .build();

        return generateAgent;
    }

    private UntypedAgent makeRouteAgent(GenerateContext context, AbortAgent abortAgent, UntypedAgent createAgent) {
        return AgenticServices.conditionalBuilder()
                .name("RouteAgent")
                .description("读取判定结果并路由到终止分支或继续执行生成分支。")
                .subAgents("valid",
                        scope -> readDecideResult(scope).valid(),
                        createAgent)
                .subAgents("invalid",
                        scope -> !readDecideResult(scope).valid(),
                        AgenticServices.agentAction(scope -> context.getAbortStep().run(scope, abortAgent)))
                .listener(context.getAgentListener())
                .build();
    }

    private UntypedAgent makeCreateAgent(GenerateContext context, PlanAgent planAgent, DraftAgent draftAgent,
                                         EvaluateAgent evaluateAgent, AmendAgent amendAgent,
                                         SummarizeAgent summarizeAgent) {
        return AgenticServices.sequenceBuilder()
                .name("CreateAgent")
                .description("顺序执行规划、起草、校验修订与总结，生成最终可落库问答集。")
                .subAgents(
                        AgenticServices.agentAction(scope -> context.getPlanStep().run(scope, planAgent)),
                        AgenticServices.agentAction(scope -> context.getWriteStep().run(scope, draftAgent)),
                        AgenticServices.agentAction(scope -> context.getValidateStep().run(scope, evaluateAgent, amendAgent)),
                        AgenticServices.agentAction(scope -> context.getSummarizeStep().run(scope, summarizeAgent))
                )
                .output(scope -> scope.readState("qaSetId"))
                .build();
    }

    private DecideResult readDecideResult(AgenticScope scope) {
        Object value = scope.readState("decideResult");
        return value instanceof DecideResult result
                ? result
                : new DecideResult(false, "请求判定未完成");
    }

    private DecideAgent makeDecideAgent(GenerateContext context) {
        return AgenticServices.agentBuilder(DecideAgent.class)
                .name("DecideAgent")
                .description("识别用户请求是否满足问答集生成场景并给出判定结果。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener())
                .build();
    }

    private AbortAgent makeAbortAgent(GenerateContext context) {
        return AgenticServices.agentBuilder(AbortAgent.class)
                .name("AbortAgent")
                .description("根据拒绝原因生成终止消息并结束当前生成任务。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener())
                .build();
    }

    private PlanAgent makePlanAgent(GenerateContext context) {
        AgentBuilder<PlanAgent, ?> builder = AgenticServices.agentBuilder(PlanAgent.class)
                .name("PlanAgent")
                .description("分析资料摘要并输出模块化题量与难度分配计划。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener());
        addTools(builder, context.getWriteTools());
        return builder.build();
    }

    private DraftAgent makeDraftAgent(GenerateContext context) {
        AgentBuilder<DraftAgent, ?> builder = AgenticServices.agentBuilder(DraftAgent.class)
                .name("DraftAgent")
                .description("基于检索证据按模块起草结构化问答题目。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener());
        addTools(builder, context.getWriteTools());
        return builder.build();
    }

    private EvaluateAgent makeEvaluateAgent(GenerateContext context) {
        return AgenticServices.agentBuilder(EvaluateAgent.class)
                .name("EvaluateAgent")
                .description("审校题目准确性、完整性与证据边界并输出判定。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener())
                .build();
    }

    private AmendAgent makeAmendAgent(GenerateContext context) {
        AgentBuilder<AmendAgent, ?> builder = AgenticServices.agentBuilder(AmendAgent.class)
                .name("AmendAgent")
                .description("按审校建议进行最小必要修订并保持题目结构稳定。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener());
        addTools(builder, context.getValidateTools());
        return builder.build();
    }

    private SummarizeAgent makeSummarizeAgent(GenerateContext context) {
        return AgenticServices.agentBuilder(SummarizeAgent.class)
                .name("SummarizeAgent")
                .description("汇总生成结果与统计信息并输出最终完成说明。")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getAgentListener())
                .build();
    }

    private void addTools(AgentBuilder<?, ?> builder, List<Object> tools) {
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools.toArray());
        }
    }

}
