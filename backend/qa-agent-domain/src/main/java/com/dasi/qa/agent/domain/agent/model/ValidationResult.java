package com.dasi.qa.agent.domain.agent.model;

import com.dasi.qa.agent.domain.agent.model.enumeration.VerdictType;
import dev.langchain4j.model.output.structured.Description;

public record ValidationResult(
        @Description("题目在数组中的索引") int itemIndex,
        @Description("校验结论") VerdictType verdictType,
        @Description("判定原因") String reason,
        @Description("修改建议，REVISE 时提供") String revisionSuggestion
) {
}
