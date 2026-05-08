package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AbortAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DecideAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DraftAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlanAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SummarizeAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class GenerateContext {

    private final ChatModel userModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final AgentListener agentListener;
    private final List<Object> writeTools;
    private final List<Object> validateTools;
    private final DecideStep decideStep;
    private final AbortStep abortStep;
    private final PlanStep planStep;
    private final WriteStep writeStep;
    private final ValidateStep validateStep;
    private final SummarizeStep summarizeStep;

    @FunctionalInterface
    public interface DecideStep {
        void run(AgenticScope scope, DecideAgent decideAgent);
    }

    @FunctionalInterface
    public interface AbortStep {
        void run(AgenticScope scope, AbortAgent abortAgent);
    }

    @FunctionalInterface
    public interface PlanStep {
        void run(AgenticScope scope, PlanAgent planAgent);
    }

    @FunctionalInterface
    public interface WriteStep {
        void run(AgenticScope scope, DraftAgent draftAgent);
    }

    @FunctionalInterface
    public interface ValidateStep {
        void run(AgenticScope scope, EvaluateAgent evaluateAgent, AmendAgent amendAgent);
    }

    @FunctionalInterface
    public interface SummarizeStep {
        void run(AgenticScope scope, SummarizeAgent summarizeAgent);
    }

}
