package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import com.dasi.qa.agent.domain.agent.shared.enumeration.VerdictType;
import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    @Description("题目在数组中的索引")
    private int itemIndex;

    @Description("校验结论")
    private VerdictType verdictType;

    @Description("判定原因")
    private String reason;

    @Description("修改建议，REVISE 时提供")
    private String revisionSuggestion;
}
