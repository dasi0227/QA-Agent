package com.dasi.qa.agent.domain.agent.service.memory.model.context;

import com.dasi.qa.agent.domain.agent.service.memory.subagent.InvestAgent;
import com.dasi.qa.agent.domain.agent.service.memory.subagent.MergeAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class MemoryContext {

    private final ChatModel userModel;
    private final InvestStep investStep;
    private final MergeStep mergeStep;

    @FunctionalInterface
    public interface InvestStep {
        void run(AgenticScope scope, InvestAgent investAgent);
    }

    @FunctionalInterface
    public interface MergeStep {
        void run(AgenticScope scope, MergeAgent mergeAgent);
    }
}
