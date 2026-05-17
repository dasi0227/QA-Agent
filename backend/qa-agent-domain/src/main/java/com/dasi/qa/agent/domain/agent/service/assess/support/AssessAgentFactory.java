package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviseAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnoseAgent;
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

    public UntypedAgent build(AssessContext context) {
        // 1. 创建 SubAgent
        DiagnoseAgent diagnoseAgent = makeDiagnoseAgent(context.getUserModel());
        AdviseAgent adviseAgent = makeAdviseAgent(context.getUserModel());
        RecordAgent recordAgent = makeRecordAgent(context.getUserModel());

        // 2. 组装 Agent
        AgenticServices.AgenticScopeAction diagnoseAction =
                AgenticServices.agentAction(scope -> context.getDiagnoseStep().run(scope, diagnoseAgent));
        AgenticServices.AgenticScopeAction adviseAction =
                AgenticServices.agentAction(scope -> context.getAdviseStep().run(scope, adviseAgent));
        AgenticServices.AgenticScopeAction recordAction =
                AgenticServices.agentAction(scope -> context.getRecordStep().run(scope, recordAgent));

        // 3. 组装用户评估序列和并发记忆分支
        UntypedAgent reviewAgent = makeReviewAgent(diagnoseAction, adviseAction);
        return makeAssessAgent(reviewAgent, recordAction);
    }

    private UntypedAgent makeAssessAgent(UntypedAgent userAssessmentAgent,
                                         AgenticServices.AgenticScopeAction recordAction) {
        return AgenticServices.parallelBuilder()
                .name(AssessPhase.ASSESS.getAgentName())
                .description(AssessPhase.ASSESS.getAgentDesc())
                .subAgents(userAssessmentAgent, recordAction)
                .output(scope -> "ASSESSED")
                .build();
    }

    private UntypedAgent makeReviewAgent(AgenticServices.AgenticScopeAction diagnoseAction,
                                         AgenticServices.AgenticScopeAction adviseAction) {
        return AgenticServices.sequenceBuilder()
                .name(AssessPhase.USER_ASSESSMENT.getAgentName())
                .description(AssessPhase.USER_ASSESSMENT.getAgentDesc())
                .subAgents(diagnoseAction, adviseAction)
                .build();
    }

    private DiagnoseAgent makeDiagnoseAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(DiagnoseAgent.class)
                .name(AssessPhase.DIAGNOSE.getAgentName())
                .description(AssessPhase.DIAGNOSE.getAgentDesc())
                .chatModel(userModel)
                .build();
    }

    private AdviseAgent makeAdviseAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(AdviseAgent.class)
                .name(AssessPhase.ADVISE.getAgentName())
                .description(AssessPhase.ADVISE.getAgentDesc())
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
