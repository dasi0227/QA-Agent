package com.dasi.qa.agent.domain.agent.service.feedback.model.context;

import com.dasi.qa.agent.types.dto.response.practice.FeedbackSourceChunk;
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
public class FeedbackContext {

    private String sessionItemId;
    private String sessionId;
    private String qaItemId;
    private String question;
    private String standardAnswer;
    private String knowledgeNote;
    private String tip;
    private String userAnswer;
    private Boolean unknown;
    private String answerStyle;
    private String feedbackStyle;
    private List<FeedbackSourceChunk> sourceChunks;
    private LocalDateTime previousAnsweredAt;
}
