package com.dasi.qa.agent.types.model.request.practice;

import com.dasi.qa.agent.types.model.request.BaseRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PracticeSessionItemRequest extends BaseRequest {

    private String sessionId;
    private String qaItemId;
    private Integer sortOrder;
    private String userAnswer;
    private String result;
    private Integer score;
    private String feedbackSummary;
    private LocalDateTime answeredAt;
}
