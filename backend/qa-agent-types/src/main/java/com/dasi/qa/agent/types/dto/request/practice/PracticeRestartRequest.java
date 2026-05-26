package com.dasi.qa.agent.types.dto.request.practice;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeRestartRequest {

    private String sessionId;

    @NotBlank(message = "qaSetId 不能为空")
    private String qaSetId;

    @Builder.Default
    private String mode = "SEQUENTIAL";

    private String feedbackMode;

    private String selectedModule;

    private List<String> itemIds;
}
