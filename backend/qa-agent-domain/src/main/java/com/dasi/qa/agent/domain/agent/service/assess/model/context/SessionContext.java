package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 整轮评估 DB 快照，保存 session、题集、单题摘要和 Java 计算指标。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {

    private String sessionId;
    private String qaSetId;
    private String qaSetTitle;
    private Integer totalQuestions;
    private List<AssessItemDetail> items;
    private AssessStats stats;
}
