package com.dasi.qa.agent.domain.agent.service.assess.model.enumeration;

import lombok.Getter;

/**
 * 整轮评估 DAG 阶段定义，统一维护 Agent 名称、描述和 Scope key。
 */
@Getter
public enum AssessPhase {

    ASSESS("AssessAgent", "整轮练习评估 DAG。", null),
    REVIEW("ReviewAgent", "生成用户可读的整轮诊断和复习建议。", null),
    DIAGNOSE("DiagnoseAgent", "识别本轮优势和薄弱点。", "diagnoseResult"),
    ADVISE("AdviseAgent", "生成整体点评和复习指导。", "adviseResult");

    private final String agentName;
    private final String agentDesc;
    private final String scopeKey;

    AssessPhase(String agentName, String agentDesc, String scopeKey) {
        this.agentName = agentName;
        this.agentDesc = agentDesc;
        this.scopeKey = scopeKey;
    }
}
