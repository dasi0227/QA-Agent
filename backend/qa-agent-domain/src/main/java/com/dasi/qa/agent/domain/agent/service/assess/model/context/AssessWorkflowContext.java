package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviceAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnosisAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.RecordAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AssessWorkflowContext {

    private final ChatModel userModel;
    private final PrepareStep prepareStep;
    private final DiagnosisStep diagnosisStep;
    private final AdviceStep adviceStep;
    private final RecordStep recordStep;
    private final SaveStep saveStep;

    @FunctionalInterface
    public interface PrepareStep {
        void run(AgenticScope scope);
    }

    @FunctionalInterface
    public interface DiagnosisStep {
        void run(AgenticScope scope, DiagnosisAgent diagnosisAgent);
    }

    @FunctionalInterface
    public interface AdviceStep {
        void run(AgenticScope scope, AdviceAgent adviceAgent);
    }

    @FunctionalInterface
    public interface RecordStep {
        void run(AgenticScope scope, RecordAgent recordAgent);
    }

    @FunctionalInterface
    public interface SaveStep {
        void run(AgenticScope scope);
    }
}
