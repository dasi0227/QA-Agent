package com.dasi.qa.agent.types.dto.response.qa;

import com.dasi.qa.agent.types.dto.response.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class QaSetResponse extends BaseResponse {

    private String taskId;
    private String title;
    private String description;
    private String moduleTagsJson;
    private Integer questionCount;
    private Integer practiceCount;
    private Integer averageScore;
    private Integer bestScore;
    private BigDecimal averageAccuracy;
    private BigDecimal bestAccuracy;
    private LocalDateTime lastPracticedAt;
}
