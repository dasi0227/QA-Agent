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
public class PracticeInitRequest {

    @NotBlank(message = "qaSetId 不能为空")
    private String qaSetId;

    @Builder.Default
    private String mode = "SEQUENTIAL";

    private String feedbackMode;

    private String selectedModule;
}
