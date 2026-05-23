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
public class PracticeAbandonRequest {

    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    private Integer durationSeconds;
}
