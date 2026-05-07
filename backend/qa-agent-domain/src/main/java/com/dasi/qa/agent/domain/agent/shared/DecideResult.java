package com.dasi.qa.agent.domain.agent.shared;

import dev.langchain4j.model.output.structured.Description;

public record DecideResult(
        @Description("是否与生成问答集相关") boolean valid,
        @Description("判定原因") String reason
) {
}
