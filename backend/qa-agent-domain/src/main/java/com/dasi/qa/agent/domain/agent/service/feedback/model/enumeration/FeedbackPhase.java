package com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单题反馈 DAG 阶段定义，仅维护真实 Agent 阶段。
 */
@Getter
@AllArgsConstructor
public enum FeedbackPhase {
    FEEDBACK("FeedbackAgent", "执行单题反馈链路。", null),
    HINT("HintAgent", "用户不会时生成记忆技巧和情绪支持。", "hintResult"),
    JUDGE("JudgeAgent", "用户有效作答时判定回答质量并生成反馈。", "judgeResult");

    private final String agentName;
    private final String agentDesc;
    private final String scopeKey;
}
