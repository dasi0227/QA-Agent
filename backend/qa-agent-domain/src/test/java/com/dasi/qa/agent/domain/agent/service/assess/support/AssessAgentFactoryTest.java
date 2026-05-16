package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessWorkflowContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.AssessPhase;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssessAgentFactoryTest {

    private final AssessAgentFactory factory = new AssessAgentFactory();

    @Test
    void shouldRunPrepareParallelBranchesAndSave() {
        UntypedAgent agent = factory.build(context());

        Object result = agent.invokeWithAgenticScope(Map.of()).agenticScope()
                .readState(AssessPhase.SAVE.getScopeKey());

        assertEquals("PREPARED-DIAGNOSED-ADVISED-RECORDED-SAVED", result);
    }

    private AssessWorkflowContext context() {
        return AssessWorkflowContext.builder()
                .userModel(new ChatModel() {
                })
                .prepareStep(scope -> scope.writeState("trace", "PREPARED"))
                .diagnosisStep((scope, diagnosisAgent) -> scope.writeState("trace", scope.readState("trace") + "-DIAGNOSED"))
                .adviceStep((scope, adviceAgent) -> scope.writeState("trace", scope.readState("trace") + "-ADVISED"))
                .recordStep((scope, recordAgent) -> scope.writeState("record", "RECORDED"))
                .saveStep(scope -> scope.writeState(AssessPhase.SAVE.getScopeKey(),
                        scope.readState("trace") + "-" + scope.readState("record") + "-SAVED"))
                .build();
    }
}
