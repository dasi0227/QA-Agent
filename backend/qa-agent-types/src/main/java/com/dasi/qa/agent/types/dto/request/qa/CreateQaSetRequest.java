package com.dasi.qa.agent.types.dto.request.qa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQaSetRequest {

    private String title = "未命名问答集";

    @NotBlank(message = "用户提示词不能为空")
    private String userPrompt;

    private String jobDescription = "";

    @NotEmpty(message = "请至少选择一份资料")
    private List<String> documentIds;

    @Max(value = 100, message = "单次最多生成 100 题")
    @Min(value = 10, message = "单次至少生成 10 题")
    private Integer requestedQuestionCount;

}
