package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 整轮评估中的单题输入摘要，供 DiagnoseAgent 使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessItemDetail {

    private String itemId;
    private String question;
    private String moduleTag;
    private String difficulty;
    private String standardAnswer;
    private String userAnswer;
    private String result;
    private Integer score;
    private String feedbackSummary;
    private List<String> missingPoints;
    private List<String> wrongPoints;
    private LocalDateTime answeredAt;
}
