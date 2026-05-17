package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.MemoryClueImportance;
import com.dasi.qa.agent.domain.agent.service.assess.model.enumeration.MemoryClueType;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.AdviceResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnosisResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.MemoryClueResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.StrengthResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.WeaknessResult;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.response.practice.AssessmentDetail;
import com.dasi.qa.agent.types.dto.response.practice.AssessmentPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
/**
 * AssessResultSanitizer 负责解析、裁剪和兜底整轮评估的 LLM 输出。
 */
public class AssessResultSanitizer {

    private static final int MAX_POINTS = 3;

    private final IJsonUtil jsonUtil;

    public AssessResultSanitizer(IJsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    /**
     * 解析 DiagnosisAgent 输出，并过滤空内容。
     */
    public DiagnosisResult parseDiagnosis(String response) {
        try {
            DiagnosisResult result = jsonUtil.parseJsonObject(response, DiagnosisResult.class);
            return sanitizeDiagnosis(result);
        } catch (Exception exception) {
            return fallbackDiagnosis();
        }
    }

    /**
     * 解析 AdviceAgent 输出，并补齐空点评。
     */
    public AdviceResult parseAdvice(String response, AssessMetrics metrics) {
        try {
            AdviceResult result = jsonUtil.parseJsonObject(response, AdviceResult.class);
            return sanitizeAdvice(result, metrics);
        } catch (Exception exception) {
            return fallbackAdvice(metrics);
        }
    }

    /**
     * 解析 RecordAgent 输出的根数组，并过滤非法线索。
     */
    public RecordResult parseRecord(String response) {
        try {
            List<MemoryClueResult> clues = jsonUtil.parseJsonArray(response, MemoryClueResult.class);
            return sanitizeRecord(RecordResult.builder().clues(clues).build());
        } catch (Exception exception) {
            return fallbackRecord();
        }
    }

    /**
     * 清洗优势和薄弱点列表。
     */
    public DiagnosisResult sanitizeDiagnosis(DiagnosisResult result) {
        if (result == null) {
            return fallbackDiagnosis();
        }
        return DiagnosisResult.builder()
                .strengths(sanitizeStrengths(result.getStrengths()))
                .weaknesses(sanitizeWeaknesses(result.getWeaknesses()))
                .build();
    }

    /**
     * 清洗整体点评和复习指导。
     */
    public AdviceResult sanitizeAdvice(AdviceResult result, AssessMetrics metrics) {
        if (result == null) {
            return fallbackAdvice(metrics);
        }
        String overallComment = value(result.getOverallComment());
        String reviewGuidance = value(result.getReviewGuidance());
        AdviceResult fallback = fallbackAdvice(metrics);
        return AdviceResult.builder()
                .overallComment(overallComment.isBlank() ? fallback.getOverallComment() : overallComment)
                .reviewGuidance(reviewGuidance.isBlank() ? fallback.getReviewGuidance() : reviewGuidance)
                .build();
    }

    /**
     * 清洗内部记忆线索，保留合法枚举和非空观察。
     */
    public RecordResult sanitizeRecord(RecordResult result) {
        if (result == null || result.getClues() == null) {
            return fallbackRecord();
        }
        List<MemoryClueResult> clues = new ArrayList<>();
        // 逐条过滤空观察、非法类型和超长列表
        for (MemoryClueResult clue : result.getClues()) {
            if (clue == null || value(clue.getObservation()).isBlank()) {
                continue;
            }
            MemoryClueType type = MemoryClueType.fromValue(clue.getType());
            if (type == null) {
                continue;
            }
            clues.add(MemoryClueResult.builder()
                    .type(type.name())
                    .observation(value(clue.getObservation()))
                    .importance(MemoryClueImportance.normalize(clue.getImportance()).name())
                    .build());
            if (clues.size() == MAX_POINTS) {
                break;
            }
        }
        return RecordResult.builder().clues(clues).build();
    }

    /**
     * 将 Advice 与 Diagnosis 合并为接口返回的评估详情。
     */
    public AssessmentDetail toAssessmentDetail(AdviceResult advice, DiagnosisResult diagnosis) {
        return AssessmentDetail.builder()
                .overallComment(value(advice.getOverallComment()))
                .reviewGuidance(value(advice.getReviewGuidance()))
                .strengths(toStrengthPoints(diagnosis.getStrengths()))
                .weaknesses(toWeaknessPoints(diagnosis.getWeaknesses()))
                .build();
    }

    public DiagnosisResult fallbackDiagnosis() {
        return DiagnosisResult.builder()
                .strengths(List.of())
                .weaknesses(List.of())
                .build();
    }

    public AdviceResult fallbackAdvice(AssessMetrics metrics) {
        int score = metrics == null || metrics.getScore() == null ? 0 : metrics.getScore();
        String accuracy = metrics == null || metrics.getAccuracy() == null ? "0.00" : metrics.getAccuracy().toPlainString();
        return AdviceResult.builder()
                .overallComment("本轮练习已完成，系统根据单题结果计算出总分 " + score + "，达标率 " + accuracy + "%。")
                .reviewGuidance("下一轮建议先复盘 WRONG 和 UNKNOWN 题，再回到 DEFICIENT 题补充关键点和表达结构，最后用 CORRECT 题保持熟练度。")
                .build();
    }

    public RecordResult fallbackRecord() {
        return RecordResult.builder().clues(List.of()).build();
    }

    private List<StrengthResult> sanitizeStrengths(List<StrengthResult> values) {
        if (values == null) {
            return List.of();
        }
        List<StrengthResult> results = new ArrayList<>();
        for (StrengthResult value : values) {
            if (value == null || value(value.getTitle()).isBlank() || value(value.getAnalysis()).isBlank()) {
                continue;
            }
            results.add(StrengthResult.builder()
                    .title(value(value.getTitle()))
                    .analysis(value(value.getAnalysis()))
                    .build());
            if (results.size() == MAX_POINTS) {
                break;
            }
        }
        return results;
    }

    private List<WeaknessResult> sanitizeWeaknesses(List<WeaknessResult> values) {
        if (values == null) {
            return List.of();
        }
        List<WeaknessResult> results = new ArrayList<>();
        for (WeaknessResult value : values) {
            if (value == null || value(value.getTitle()).isBlank() || value(value.getAnalysis()).isBlank()) {
                continue;
            }
            results.add(WeaknessResult.builder()
                    .title(value(value.getTitle()))
                    .analysis(value(value.getAnalysis()))
                    .build());
            if (results.size() == MAX_POINTS) {
                break;
            }
        }
        return results;
    }

    private List<AssessmentPoint> toStrengthPoints(List<StrengthResult> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> AssessmentPoint.builder()
                        .title(value(value.getTitle()))
                        .analysis(value(value.getAnalysis()))
                        .build())
                .toList();
    }

    private List<AssessmentPoint> toWeaknessPoints(List<WeaknessResult> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> AssessmentPoint.builder()
                        .title(value(value.getTitle()))
                        .analysis(value(value.getAnalysis()))
                        .build())
                .toList();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
