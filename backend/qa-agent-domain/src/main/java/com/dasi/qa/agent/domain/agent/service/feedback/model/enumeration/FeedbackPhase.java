package com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FeedbackPhase {
    FEEDBACK("FeedbackAgent", "执行单题反馈链路。", null),
    PREPARE("FeedbackPrepare", "读取练习题、题目、用户画像和资料切片，准备反馈上下文。", "feedbackContext"),
    ROUTE("FeedbackRoute", "根据 Java 规则判断 UNKNOWN 分支或 JUDGE 分支。", "isUnknown"),
    HINT("HintAgent", "用户不会时生成记忆技巧和情绪支持。", "hintResult"),
    JUDGE("JudgeAgent", "用户有效作答时判定回答质量并生成反馈。", "judgeResult"),
    SAVE("FeedbackSave", "保存单题最新反馈并返回结构化响应。", "feedbackResponse");

    private final String agentName;
    private final String agentDesc;
    private final String scopeKey;
}
