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
public class AssessDetail {

    private String overallComment;
    private String reviewGuidance;
    private List<AssessPoint> strengths;
    private List<AssessPoint> weaknesses;
}
