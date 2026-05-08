package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifficultyDistribution {

    @Description("简单题数")
    private int easy;

    @Description("中等题数")
    private int medium;

    @Description("困难题数")
    private int hard;
}
