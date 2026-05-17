package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessWorkflowContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviceAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnosisAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.RecordAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

/**
 * AssessAgentFactory 负责组装整轮评估 DAG，不承载具体业务逻辑。
 */
@Component
public class AssessAgentFactory {

    /**
     * 构建 PREPARE -> 并发评估分支 -> SAVE 的整轮评估 DAG。
     */
    public UntypedAgent build(AssessWorkflowContext context) {
        // 1. 创建 LangChain4J 子 Agent
        DiagnosisAgent diagnosisAgent = makeDiagnosisAgent(context.getUserModel());
        AdviceAgent adviceAgent = makeAdviceAgent(context.getUserModel());
        RecordAgent recordAgent = makeRecordAgent(context.getUserModel());

        // 2. 把主 Agent 阶段方法包装成 Agentic action
        AgenticServices.AgenticScopeAction prepareAction =
                AgenticServices.agentAction(context.getPrepareStep()::run);
        AgenticServices.AgenticScopeAction diagnosisAction =
                AgenticServices.agentAction(scope -> context.getDiagnosisStep().run(scope, diagnosisAgent));
        AgenticServices.AgenticScopeAction adviceAction =
                AgenticServices.agentAction(scope -> context.getAdviceStep().run(scope, adviceAgent));
        AgenticServices.AgenticScopeAction recordAction =
                AgenticServices.agentAction(scope -> context.getRecordStep().run(scope, recordAgent));
        AgenticServices.AgenticScopeAction saveAction =
                AgenticServices.agentAction(context.getSaveStep()::run);

        // 3. 组装用户评估序列和并发记忆分支
        UntypedAgent userAssessmentAgent = makeUserAssessmentAgent(diagnosisAction, adviceAction);
        UntypedAgent parallelAgent = makeParallelAgent(userAssessmentAgent, recordAction);
        return AgenticServices.sequenceBuilder()
                .name(AssessPhase.ASSESS.getAgentName())
                .description(AssessPhase.ASSESS.getAgentDesc())
                .subAgents(prepareAction, parallelAgent, saveAction)
                .build();
    }

    /**
     * 用户可读评估分支按诊断到建议的顺序执行。
     */
    private UntypedAgent makeUserAssessmentAgent(AgenticServices.AgenticScopeAction diagnosisAction,
                                                 AgenticServices.AgenticScopeAction adviceAction) {
        return AgenticServices.sequenceBuilder()
                .name(AssessPhase.USER_ASSESSMENT.getAgentName())
                .description(AssessPhase.USER_ASSESSMENT.getAgentDesc())
                .subAgents(diagnosisAction, adviceAction)
                .build();
    }

    /**
     * 并发执行用户评估分支和内部记忆线索分支。
     */
    private UntypedAgent makeParallelAgent(UntypedAgent userAssessmentAgent,
                                           AgenticServices.AgenticScopeAction recordAction) {
        return AgenticServices.parallelBuilder()
                .name(AssessPhase.PARALLEL.getAgentName())
                .description(AssessPhase.PARALLEL.getAgentDesc())
                .subAgents(userAssessmentAgent, recordAction)
                .output(scope -> "ASSESSED")
                .build();
    }

    private DiagnosisAgent makeDiagnosisAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(DiagnosisAgent.class)
                .name(AssessPhase.DIAGNOSIS.getAgentName())
                .description(AssessPhase.DIAGNOSIS.getAgentDesc())
                .chatModel(userModel)
                .build();
    }

    private AdviceAgent makeAdviceAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(AdviceAgent.class)
                .name(AssessPhase.ADVICE.getAgentName())
                .description(AssessPhase.ADVICE.getAgentDesc())
                .chatModel(userModel)
                .build();
    }

    private RecordAgent makeRecordAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(RecordAgent.class)
                .name(AssessPhase.RECORD.getAgentName())
                .description(AssessPhase.RECORD.getAgentDesc())
                .chatModel(userModel)
                .build();
    }
}
