package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
/**
 * FeedbackScorePolicy 负责约束单题反馈结果和离散分数。
 */
public class FeedbackScorePolicy {

    private static final Map<FeedbackResultType, Set<Integer>> ALLOWED_SCORES = Map.of(
            FeedbackResultType.CORRECT, Set.of(80, 90, 100),
            FeedbackResultType.DEFICIENT, Set.of(40, 50, 60, 70),
            FeedbackResultType.WRONG, Set.of(0, 10, 20, 30),
            FeedbackResultType.UNKNOWN, Set.of(0)
    );

    private static final Map<FeedbackResultType, Integer> DEFAULT_SCORES = Map.of(
            FeedbackResultType.CORRECT, 90,
            FeedbackResultType.DEFICIENT, 60,
            FeedbackResultType.WRONG, 20,
            FeedbackResultType.UNKNOWN, 0
    );

    /**
     * 将 LLM 输出的结果收敛到已知枚举。
     */
    public FeedbackResultType normalizeResult(String rawResult) {
        // 空值和非法值统一降级为 DEFICIENT
        if (rawResult == null || rawResult.isBlank()) {
            return FeedbackResultType.DEFICIENT;
        }
        try {
            return FeedbackResultType.valueOf(rawResult.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return FeedbackResultType.DEFICIENT;
        }
    }

    /**
     * 将分数修正到当前结果允许的离散分值。
     */
    public Integer normalizeScore(FeedbackResultType result, Integer score) {
        // 合法分值原样保留，否则使用该结果类型默认分
        FeedbackResultType normalizedResult = result != null ? result : FeedbackResultType.DEFICIENT;
        if (score != null && ALLOWED_SCORES.get(normalizedResult).contains(score)) {
            return score;
        }
        return DEFAULT_SCORES.get(normalizedResult);
    }

    /**
     * Judge 分支只允许输出有效作答的三类结果。
     */
    public boolean allowedForJudge(FeedbackResultType result) {
        return result == FeedbackResultType.CORRECT
                || result == FeedbackResultType.DEFICIENT
                || result == FeedbackResultType.WRONG;
    }
}
