package com.dasi.qa.agent.types.dto.response.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeDetail {
    private List<String> missingPoints;
    private List<String> wrongPoints;
    private String improvementAdvice;
    private String commonPitfall;
}
