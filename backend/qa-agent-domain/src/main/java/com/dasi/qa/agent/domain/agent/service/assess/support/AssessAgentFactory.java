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

@Component
public class AssessAgentFactory {

    public UntypedAgent build(AssessWorkflowContext context) {
        DiagnosisAgent diagnosisAgent = makeDiagnosisAgent(context.getUserModel());
        AdviceAgent adviceAgent = makeAdviceAgent(context.getUserModel());
        RecordAgent recordAgent = makeRecordAgent(context.getUserModel());

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

        UntypedAgent userAssessmentAgent = makeUserAssessmentAgent(diagnosisAction, adviceAction);
        UntypedAgent parallelAgent = makeParallelAgent(userAssessmentAgent, recordAction);
        return AgenticServices.sequenceBuilder()
                .name(AssessPhase.ASSESS.getAgentName())
                .description(AssessPhase.ASSESS.getAgentDesc())
                .subAgents(prepareAction, parallelAgent, saveAction)
                .build();
    }

    private UntypedAgent makeUserAssessmentAgent(AgenticServices.AgenticScopeAction diagnosisAction,
                                                 AgenticServices.AgenticScopeAction adviceAction) {
        return AgenticServices.sequenceBuilder()
                .name(AssessPhase.USER_ASSESSMENT.getAgentName())
                .description(AssessPhase.USER_ASSESSMENT.getAgentDesc())
                .subAgents(diagnosisAction, adviceAction)
                .build();
    }

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
