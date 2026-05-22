package com.dasi.qa.agent.domain.qa.service.convert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaSetExportFile {

    private Integer schemaVersion;

    private String app;

    private String exportedAt;

    private QaSetMetaInfo qaQaSetMetaInfo;

    private List<QaSetEntry> qaSetEntries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QaSetMetaInfo {

        private String title;

        private String description;

        private List<String> moduleTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QaSetEntry {

        private String question;

        private String answer;

        private String knowledgeNote;

        private String moduleTag;

        private String difficulty;

        private String keywords;

        private String hint;

        private Boolean sourceReliable;

        private Integer sortOrder;
    }
}
