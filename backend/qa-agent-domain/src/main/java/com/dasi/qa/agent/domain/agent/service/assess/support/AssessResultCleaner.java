package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult.DiagnoseItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AssessResultCleaner 负责裁剪和归一整轮评估的 LLM 输出。
 */
@Component
public class AssessResultCleaner {

    private static final int MAX_POINTS = 3;

    public DiagnoseResult cleanDiagnosis(DiagnoseResult result) {
        if (result == null) {
            return null;
        }
        return DiagnoseResult.builder()
                .strengths(cleanDiagnoseItems(result.getStrengths()))
                .weaknesses(cleanDiagnoseItems(result.getWeaknesses()))
                .build();
    }

    private List<DiagnoseItem> cleanDiagnoseItems(List<DiagnoseItem> values) {
        if (values == null) {
            return List.of();
        }
        List<DiagnoseItem> results = new ArrayList<>();
        for (DiagnoseItem value : values) {
            if (value == null || !StringUtils.hasText(value.getTitle()) || !StringUtils.hasText(value.getAnalysis())) {
                continue;
            }
            results.add(DiagnoseItem.builder()
                    .title(value.getTitle().trim())
                    .analysis(value.getAnalysis().trim())
                    .build());
            if (results.size() == MAX_POINTS) {
                break;
            }
        }
        return results;
    }

    public AdviseResult cleanAdvise(AdviseResult result) {
        if (result == null || result.getOverallComment() == null || result.getReviewGuidance() == null) {
            return null;
        }
        return AdviseResult.builder()
                .overallComment(result.getOverallComment().trim())
                .reviewGuidance(result.getReviewGuidance().trim())
                .build();
    }

}
