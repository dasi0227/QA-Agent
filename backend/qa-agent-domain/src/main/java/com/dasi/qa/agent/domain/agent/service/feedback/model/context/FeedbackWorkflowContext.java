package com.dasi.qa.agent.domain.agent.service.feedback.model.context;

import com.dasi.qa.agent.domain.agent.service.feedback.subagent.HintAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.subagent.JudgeAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 单题反馈 DAG 运行上下文，保存模型和各阶段回调。
 */
@Data
@Builder
@AllArgsConstructor
public class FeedbackWorkflowContext {

    private final ChatModel userModel;
    private final PrepareStep prepareStep;
    private final HintStep hintStep;
    private final JudgeStep judgeStep;
    private final SaveStep saveStep;

    @FunctionalInterface
    public interface PrepareStep {
        void run(AgenticScope scope);
    }

    @FunctionalInterface
    public interface HintStep {
        void run(AgenticScope scope, HintAgent hintAgent);
    }

    @FunctionalInterface
    public interface JudgeStep {
        void run(AgenticScope scope, JudgeAgent judgeAgent);
    }

    @FunctionalInterface
    public interface SaveStep {
        void run(AgenticScope scope);
    }
}
