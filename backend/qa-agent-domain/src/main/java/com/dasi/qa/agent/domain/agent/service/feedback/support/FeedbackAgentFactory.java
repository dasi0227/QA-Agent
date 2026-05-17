package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackPhase;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.HintAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.JudgeAgent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

/**
 * FeedbackAgentFactory 负责组装单题反馈 DAG，不承载具体业务逻辑。
 */
@Component
public class FeedbackAgentFactory {

    public UntypedAgent build(FeedbackContext context) {
        // 1. 创建 SubAgent
        HintAgent hintAgent = makeHintAgent(context.getUserModel());
        JudgeAgent judgeAgent = makeJudgeAgent(context.getUserModel());

        // 2. 组装 Agent
        AgenticServices.AgenticScopeAction hintAction =
                AgenticServices.agentAction(scope -> context.getHintStep().run(scope, hintAgent));
        AgenticServices.AgenticScopeAction judgeAction =
                AgenticServices.agentAction(scope -> context.getJudgeStep().run(scope, judgeAgent));
        String routeFlagKey = context.getRouteFlagKey();
        return makeFeedbackAgent(hintAction, judgeAction, routeFlagKey);
    }

    private UntypedAgent makeFeedbackAgent(AgenticServices.AgenticScopeAction hintAction,
                                           AgenticServices.AgenticScopeAction judgeAction,
                                           String routeFlagKey) {
        return AgenticServices.conditionalBuilder()
                .name(FeedbackPhase.FEEDBACK.getAgentName())
                .description(FeedbackPhase.FEEDBACK.getAgentDesc())
                .subAgents(scope -> Boolean.TRUE.equals(scope.readState(routeFlagKey)), hintAction)
                .subAgents(scope -> Boolean.FALSE.equals(scope.readState(routeFlagKey)), judgeAction)
                .build();
    }

    private HintAgent makeHintAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(HintAgent.class)
                .name(FeedbackPhase.HINT.getAgentName())
                .description(FeedbackPhase.HINT.getAgentDesc())
                .chatModel(userModel)
                .build();
    }

    private JudgeAgent makeJudgeAgent(ChatModel userModel) {
        return AgenticServices.agentBuilder(JudgeAgent.class)
                .name(FeedbackPhase.JUDGE.getAgentName())
                .description(FeedbackPhase.JUDGE.getAgentDesc())
                .chatModel(userModel)
                .build();
    }
}
