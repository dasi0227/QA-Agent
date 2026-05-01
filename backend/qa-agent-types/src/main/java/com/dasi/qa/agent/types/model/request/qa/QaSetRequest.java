package com.dasi.qa.agent.types.model.request.qa;

import com.dasi.qa.agent.types.model.request.BaseRequest;
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
public class QaSetRequest extends BaseRequest {

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
