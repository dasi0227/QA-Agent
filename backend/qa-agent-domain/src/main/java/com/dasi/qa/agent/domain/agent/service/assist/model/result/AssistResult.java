package com.dasi.qa.agent.domain.agent.service.assist.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistResult {

    @Description("3-6 个逗号分隔的答题关键词短语")
    private String keywords;

    @Description("一句不泄露答案的答前提示")
    private String hint;
}
