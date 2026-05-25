package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import com.dasi.qa.agent.domain.agent.service.assess.subagent.AdviseAgent;
import com.dasi.qa.agent.domain.agent.service.assess.subagent.DiagnoseAgent;
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
public class AssessContext {

    private final ChatModel userModel;
    private final DiagnoseStep diagnoseStep;
    private final AdviseStep adviseStep;

    @FunctionalInterface
    public interface DiagnoseStep {
        void run(AgenticScope scope, DiagnoseAgent diagnoseAgent);
    }

    @FunctionalInterface
    public interface AdviseStep {
        void run(AgenticScope scope, AdviseAgent adviseAgent);
    }
}
