package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.domain.agent.service.feedback.model.context.FeedbackWorkflowContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackPhase;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeedbackAgentFactoryTest {

    private final FeedbackAgentFactory factory = new FeedbackAgentFactory();

    @Test
    void shouldRouteToHintWhenUnknown() {
        UntypedAgent agent = factory.build(context(true));

        Object result = agent.invokeWithAgenticScope(Map.of()).agenticScope()
                .readState(FeedbackPhase.SAVE.getScopeKey());

        assertEquals("HINT", result);
    }

    @Test
    void shouldRouteToJudgeWhenAnswered() {
        UntypedAgent agent = factory.build(context(false));

        Object result = agent.invokeWithAgenticScope(Map.of()).agenticScope()
                .readState(FeedbackPhase.SAVE.getScopeKey());

        assertEquals("JUDGE", result);
    }

    private FeedbackWorkflowContext context(boolean unknown) {
        return FeedbackWorkflowContext.builder()
                .userModel(new ChatModel() {
                })
                .prepareStep(scope -> scope.writeState(FeedbackPhase.ROUTE.getScopeKey(), unknown))
                .hintStep((scope, hintAgent) -> scope.writeState(FeedbackPhase.SAVE.getScopeKey(), "HINT"))
                .judgeStep((scope, judgeAgent) -> scope.writeState(FeedbackPhase.SAVE.getScopeKey(), "JUDGE"))
                .saveStep(scope -> {
                })
                .build();
    }
}
