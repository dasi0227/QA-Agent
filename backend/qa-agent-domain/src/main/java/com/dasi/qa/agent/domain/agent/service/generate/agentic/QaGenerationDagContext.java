package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmenderAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DrafterAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluatorAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlannerAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.agentic.observability.AgentListener;

import java.util.List;
import java.util.concurrent.Executor;

public record QaGenerationDagContext(
        String taskId,
        ChatModel userModel,
        ChatMemoryProvider chatMemoryProvider,
        AgentListener listener,
        List<Object> creatorTools,
        List<Object> amendmentTools,
        Executor creatorExecutor,
        PlannerStep plannerStep,
        CreatorStep creatorStep,
        ValidatorStep validatorStep,
        SummarizerStep summarizerStep
) {

    @FunctionalInterface
    public interface PlannerStep {

        void run(AgenticScope scope, PlannerAgent plannerAgent);

    }

    @FunctionalInterface
    public interface CreatorStep {

        void run(AgenticScope scope, DrafterAgent drafterAgent, Executor creatorExecutor);

    }

    @FunctionalInterface
    public interface ValidatorStep {

        void run(AgenticScope scope, EvaluatorAgent evaluatorAgent, AmenderAgent amenderAgent);

    }

    @FunctionalInterface
    public interface SummarizerStep {

        void run(AgenticScope scope);

    }

}
