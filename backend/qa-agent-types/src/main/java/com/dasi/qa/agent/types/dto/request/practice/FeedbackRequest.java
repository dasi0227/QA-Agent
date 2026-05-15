package com.dasi.qa.agent.types.dto.request.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    private String sessionItemId;
    private String userAnswer;
    private Boolean unknown;
}
