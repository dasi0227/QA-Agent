package com.dasi.qa.agent.domain.memory.model.vo;

import com.dasi.qa.agent.domain.memory.model.dto.Memory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestContext {

    private String sessionId;
    private String userId;
    private String qaSetId;
    private List<IngestItem> items;
    private List<Memory> existingMemories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestItem {

        private String sessionItemId;
        private String qaItemId;
        private String question;
        private String moduleTag;
        private String difficulty;
        private String standardAnswer;
        private String userAnswer;
        private String result;
        private Integer score;
        private String feedbackSummary;
        private String missingPointsJson;
        private String wrongPointsJson;
        private String sourceChunkIdsJson;
    }
}
