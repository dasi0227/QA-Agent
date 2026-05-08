package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.agentic.scope.AgenticScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecideResult {

    @Description("是否与生成问答集相关")
    private boolean valid;

    @Description("判定原因")
    private String reason;

    public static DecideResult fromScope(AgenticScope scope) {
        Object value = scope.readState("decideResult");
        return value instanceof DecideResult result
                ? result
                : new DecideResult(false, "请求判定未完成");
    }
}
