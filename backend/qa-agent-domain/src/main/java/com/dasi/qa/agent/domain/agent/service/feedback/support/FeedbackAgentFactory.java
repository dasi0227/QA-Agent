package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackWorkflowContext;
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

    /**
     * 构建 PREPARE -> ROUTE(HINT/JUDGE) -> SAVE 的反馈 DAG。
     */
    public UntypedAgent build(FeedbackWorkflowContext context) {
        // 1. 创建 LangChain4J 子 Agent
        HintAgent hintAgent = makeHintAgent(context.getUserModel());
        JudgeAgent judgeAgent = makeJudgeAgent(context.getUserModel());

        // 2. 把主 Agent 阶段方法包装成 Agentic action
        AgenticServices.AgenticScopeAction prepareAction =
                AgenticServices.agentAction(context.getPrepareStep()::run);
        AgenticServices.AgenticScopeAction hintAction =
                AgenticServices.agentAction(scope -> context.getHintStep().run(scope, hintAgent));
        AgenticServices.AgenticScopeAction judgeAction =
                AgenticServices.agentAction(scope -> context.getJudgeStep().run(scope, judgeAgent));
        AgenticServices.AgenticScopeAction saveAction =
                AgenticServices.agentAction(context.getSaveStep()::run);

        // 3. 组装路由分支和主链路
        UntypedAgent routeAgent = makeRouteAgent(hintAction, judgeAction);
        return AgenticServices.sequenceBuilder()
                .name(FeedbackPhase.FEEDBACK.getAgentName())
                .description(FeedbackPhase.FEEDBACK.getAgentDesc())
                .subAgents(prepareAction, routeAgent, saveAction)
                .build();
    }

    /**
     * 根据 PREPARE 写入的 route flag 选择 Hint 或 Judge 分支。
     */
    private UntypedAgent makeRouteAgent(AgenticServices.AgenticScopeAction hintAction,
                                        AgenticServices.AgenticScopeAction judgeAction) {
        return AgenticServices.conditionalBuilder()
                .name(FeedbackPhase.ROUTE.getAgentName())
                .description(FeedbackPhase.ROUTE.getAgentDesc())
                .subAgents(scope -> Boolean.TRUE.equals(scope.readState(FeedbackPhase.ROUTE.getScopeKey())), hintAction)
                .subAgents(scope -> !Boolean.TRUE.equals(scope.readState(FeedbackPhase.ROUTE.getScopeKey())), judgeAction)
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
