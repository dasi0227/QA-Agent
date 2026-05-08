package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;

public record DifficultyDistribution(
        @Description("简单题数") int easy,
        @Description("中等题数") int medium,
        @Description("困难题数") int hard
) {
}
