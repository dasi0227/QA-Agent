package com.dasi.qa.agent.domain.agent.service.assess.model.enumeration;

import lombok.Getter;

@Getter
public enum AssessPhase {

    ASSESS("AssessAgent", "整轮练习评估 DAG。", null),
    PREPARE("AssessPrepare", "读取练习会话、单题结果和题目，并计算稳定指标。", "assessContext"),
    USER_ASSESSMENT("UserAssessment", "生成用户可读的整轮诊断和复习建议。", null),
    PARALLEL("AssessParallel", "并发执行用户评估和记忆线索提取。", null),
    DIAGNOSIS("DiagnosisAgent", "识别本轮优势和薄弱点。", "diagnosisResult"),
    ADVICE("AdviceAgent", "生成整体点评和复习指导。", "adviceResult"),
    RECORD("RecordAgent", "提炼 V6 Memory 使用的内部记忆线索。", "recordResult"),
    SAVE("AssessSave", "保存整轮评估结果并返回结构化响应。", "assessResponse");

    private final String agentName;
    private final String agentDesc;
    private final String scopeKey;

    AssessPhase(String agentName, String agentDesc, String scopeKey) {
        this.agentName = agentName;
        this.agentDesc = agentDesc;
        this.scopeKey = scopeKey;
    }
}
