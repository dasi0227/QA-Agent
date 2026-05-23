package com.dasi.qa.agent.types.dto.request.practice;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemSaveRequest {

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    @NotBlank(message = "sessionItemId 不能为空")
    private String sessionItemId;

    private String userAnswer;

    private Integer currentIndex;

    private Integer durationSeconds;
}
