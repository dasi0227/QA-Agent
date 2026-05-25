package com.dasi.qa.agent.domain.agent.service.memory.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MemoryPhase {

    MEMORY("MemoryAgent", "沉淀用户长期学习画像", null),
    INVEST("InvestAction", "从本轮练习评估结果中提取候选用户画像", "InvestResult"),
    MERGE("MergeAction", "将本轮候选画像合并进长期 Memory", null);

    private final String agentName;
    private final String agentDesc;
    private final String scopeKey;
}
