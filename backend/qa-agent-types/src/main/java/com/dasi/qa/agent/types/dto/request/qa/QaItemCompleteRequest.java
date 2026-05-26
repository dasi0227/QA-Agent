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
public class QaItemCompleteRequest {

    @NotBlank(message = "题目 ID 不能为空")
    private String id;

    @NotBlank(message = "问题不能为空")
    private String question;

    private String answer;
}
