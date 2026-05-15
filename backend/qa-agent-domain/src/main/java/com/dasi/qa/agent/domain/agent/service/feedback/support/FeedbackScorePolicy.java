package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
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

    public FeedbackResultType normalizeResult(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return FeedbackResultType.DEFICIENT;
        }
        try {
            return FeedbackResultType.valueOf(rawResult.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return FeedbackResultType.DEFICIENT;
        }
    }

    public Integer normalizeScore(FeedbackResultType result, Integer score) {
        FeedbackResultType normalizedResult = result != null ? result : FeedbackResultType.DEFICIENT;
        if (score != null && ALLOWED_SCORES.get(normalizedResult).contains(score)) {
            return score;
        }
        return DEFAULT_SCORES.get(normalizedResult);
    }

    public boolean allowedForJudge(FeedbackResultType result) {
        return result == FeedbackResultType.CORRECT
                || result == FeedbackResultType.DEFICIENT
                || result == FeedbackResultType.WRONG;
    }
}
