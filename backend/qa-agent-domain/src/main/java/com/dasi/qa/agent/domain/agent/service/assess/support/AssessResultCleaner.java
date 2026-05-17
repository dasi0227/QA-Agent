package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.MemoryClueImportance;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.MemoryClueType;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnoseResult.DiagnoseItem;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.MemoryClueResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * AssessResultCleaner 负责裁剪和归一整轮评估的 LLM 输出。
 */
@Component
public class AssessResultCleaner {

    private static final int MAX_POINTS = 5;

    public DiagnoseResult cleanDiagnosis(DiagnoseResult result) {
        return DiagnoseResult.builder()
                .strengths(cleanDiagnoseItems(result.getStrengths()))
                .weaknesses(cleanDiagnoseItems(result.getWeaknesses()))
                .build();
    }

    public AdviseResult cleanAdvise(AdviseResult result) {
        return AdviseResult.builder()
                .overallComment(result.getOverallComment().trim())
                .reviewGuidance(result.getReviewGuidance().trim())
                .build();
    }

    public RecordResult cleanRecord(RecordResult result) {
        if (result == null || result.getClues() == null) {
            return null;
        }
        List<MemoryClueResult> clues = new ArrayList<>();
        // 逐条过滤空观察、非法类型和超长列表
        for (MemoryClueResult clue : result.getClues()) {
            if (clue == null || !StringUtils.hasText(clue.getObservation())) {
                continue;
            }
            MemoryClueType type = MemoryClueType.fromValue(clue.getType());
            if (type == null) {
                continue;
            }
            clues.add(MemoryClueResult.builder()
                    .type(type.name())
                    .observation(clue.getObservation().trim())
                    .importance(MemoryClueImportance.normalize(clue.getImportance()).name())
                    .build());
            if (clues.size() == MAX_POINTS) {
                break;
            }
        }
        return RecordResult.builder().clues(clues).build();
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

}
