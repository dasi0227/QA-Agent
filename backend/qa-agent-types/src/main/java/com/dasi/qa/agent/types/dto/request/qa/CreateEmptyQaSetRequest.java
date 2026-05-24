package com.dasi.qa.agent.types.dto.request.qa;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmptyQaSetRequest {

    @NotBlank(message = "题集标题不能为空")
    private String title;

    @Builder.Default
    private String description = "";
}
