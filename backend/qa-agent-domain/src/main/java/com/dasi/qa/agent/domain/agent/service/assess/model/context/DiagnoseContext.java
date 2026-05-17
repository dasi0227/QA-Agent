package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DiagnoseAgent 输入上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnoseContext {

    private String sessionId;
    private String qaSetTitle;
    private String metricsJson;
    private String itemsJson;
}
