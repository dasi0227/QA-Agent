package com.dasi.qa.agent.types.dto.response.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    private String sessionItemId;
    private String qaItemId;
    private String result;
    private Integer score;
    private String feedbackSummary;
    private JudgeDetail judgeDetail;
    private HintDetail hintDetail;
    private List<SourceChunk> sourceChunks;
    private LocalDateTime answeredAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceChunk {
        private String chunkId;
        private String documentId;
        private String titlePath;
        private String summary;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JudgeDetail {
        private List<String> missingPoints;
        private List<String> wrongPoints;
        private String improvementAdvice;
        private String betterAnswer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HintDetail {
        private String memoryTip;
        private String encouragement;
    }
}
