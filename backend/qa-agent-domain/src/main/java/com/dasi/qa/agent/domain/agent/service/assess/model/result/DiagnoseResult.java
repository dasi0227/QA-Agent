package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DiagnoseAgent 输出结果，包含优势和薄弱点列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnoseResult {

    private List<DiagnoseItem> strengths;
    private List<DiagnoseItem> weaknesses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnoseItem {

        private String title;
        private String analysis;
    }
}
