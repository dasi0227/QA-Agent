package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviceAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnosisAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.RecordAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 整轮评估 DAG 运行上下文，保存模型和各阶段回调。
 */
@Data
@Builder
@AllArgsConstructor
public class AssessWorkflowContext {

    private final ChatModel userModel;
    private final DiagnosisStep diagnosisStep;
    private final AdviceStep adviceStep;
    private final RecordStep recordStep;

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
}
