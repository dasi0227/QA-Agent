package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.model.DecideResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.GenerateContext;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AbortAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DecideAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DraftAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlanAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SearchAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SummarizeAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 为单次问答集生成任务构建 LangChain4j DAG。
 */
@Component
public class GenerateAgentFactory {

    private final SummarizeAgent summarizeAgent;
    private final SearchAgent searchAgent;

    public GenerateAgentFactory(SummarizeAgent summarizeAgent, SearchAgent searchAgent) {
        this.summarizeAgent = summarizeAgent;
        this.searchAgent = searchAgent;
    }

    public UntypedAgent build(GenerateContext context) {
        DecideAgent decideAgent = decideAgent(context);
        AbortAgent abortAgent = new AbortAgent();
        PlanAgent planAgent = planAgent(context);
        DraftAgent draftAgent = draftAgent(context);
        EvaluateAgent evaluateAgent = evaluateAgent(context);
        AmendAgent amendAgent = amendAgent(context);
        UntypedAgent decideGate = decideGate(context, abortAgent);
        GenerateContext.DecideStep decideStep = context.getDecideStep();

        return AgenticServices.sequenceBuilder()
                .name("QA_GENERATION_DAG")
                .description("Decide -> Planner -> Creator -> Validator -> Summarizer")
                .subAgents(
                        AgenticServices.agentAction(scope -> decideStep.run(scope, decideAgent)),
                        decideGate,
                        AgenticServices.agentAction(scope -> context.getPlanStep().run(scope, planAgent)),
                        AgenticServices.agentAction(scope -> context.getCreateStep().run(scope, draftAgent, searchAgent)),
                        AgenticServices.agentAction(scope -> context.getValidateStep().run(scope, evaluateAgent, amendAgent)),
                        AgenticServices.agentAction(scope -> context.getSummarizeStep().run(scope, summarizeAgent))
                )
                .output(scope -> scope.readState("qaSetId"))
                .build();
    }

    private UntypedAgent decideGate(GenerateContext context, AbortAgent abortAgent) {
        return AgenticServices.conditionalBuilder()
                .name("DECIDE_GATE")
                .description("判断请求是否可以进入生成 DAG")
                .subAgents("valid",
                        scope -> readDecideResult(scope).valid(),
                        AgenticServices.agentAction(scope -> {
                        }))
                .subAgents("invalid",
                        scope -> !readDecideResult(scope).valid(),
                        AgenticServices.agentAction(scope -> context.getAbortStep().run(scope, abortAgent)))
                .listener(context.getListener())
                .build();
    }

    private DecideResult readDecideResult(AgenticScope scope) {
        Object value = scope.readState("decideResult");
        return value instanceof DecideResult result
                ? result
                : new DecideResult(false, "请求判定未完成");
    }

    private DecideAgent decideAgent(GenerateContext context) {
        return AgenticServices.agentBuilder(DecideAgent.class)
                .name("DECIDE")
                .description("判断生成请求是否可以进入问答集生成 DAG")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getListener())
                .build();
    }

    private PlanAgent planAgent(GenerateContext context) {
        AgentBuilder<PlanAgent, ?> builder = AgenticServices.agentBuilder(PlanAgent.class)
                .name("PLANNER")
                .description("分析资料结构并规划问答集模块")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getListener());
        addTools(builder, context.getCreatorTools());
        return builder.build();
    }

    private DraftAgent draftAgent(GenerateContext context) {
        AgentBuilder<DraftAgent, ?> builder = AgenticServices.agentBuilder(DraftAgent.class)
                .name("DRAFTER")
                .description("根据资料证据起草问答题目")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getListener());
        addTools(builder, context.getCreatorTools());
        return builder.build();
    }

    private EvaluateAgent evaluateAgent(GenerateContext context) {
        return AgenticServices.agentBuilder(EvaluateAgent.class)
                .name("EVALUATOR")
                .description("审校题目事实准确性和证据边界")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getListener())
                .build();
    }

    private AmendAgent amendAgent(GenerateContext context) {
        AgentBuilder<AmendAgent, ?> builder = AgenticServices.agentBuilder(AmendAgent.class)
                .name("AMENDER")
                .description("按审校意见最小修订问答题目")
                .chatModel(context.getUserModel())
                .chatMemoryProvider(context.getChatMemoryProvider())
                .listener(context.getListener());
        addTools(builder, context.getAmendmentTools());
        return builder.build();
    }

    private void addTools(AgentBuilder<?, ?> builder, List<Object> tools) {
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools.toArray());
        }
    }

}
