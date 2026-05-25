package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviseAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnoseAgent;
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
        DiagnoseAgent diagnoseAgent = makeDiagnoseAgent(context.getUserModel());
        AdviseAgent adviseAgent = makeAdviseAgent(context.getUserModel());

        AgenticServices.AgenticScopeAction diagnoseAction =
                AgenticServices.agentAction(scope -> context.getDiagnoseStep().run(scope, diagnoseAgent));
        AgenticServices.AgenticScopeAction adviseAction =
                AgenticServices.agentAction(scope -> context.getAdviseStep().run(scope, adviseAgent));

        return AgenticServices.sequenceBuilder()
                .name(AssessPhase.REVIEW.getAgentName())
                .description(AssessPhase.REVIEW.getAgentDesc())
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
}
