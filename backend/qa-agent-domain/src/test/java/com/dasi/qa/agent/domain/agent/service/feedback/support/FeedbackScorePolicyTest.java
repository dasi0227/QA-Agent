package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeedbackScorePolicyTest {

    private final FeedbackScorePolicy policy = new FeedbackScorePolicy();

    @Test
    void shouldKeepAllowedScores() {
        assertEquals(100, policy.normalizeScore(FeedbackResultType.CORRECT, 100));
        assertEquals(70, policy.normalizeScore(FeedbackResultType.DEFICIENT, 70));
        assertEquals(0, policy.normalizeScore(FeedbackResultType.WRONG, 0));
    }

    @Test
    void shouldFallbackInvalidScores() {
        assertEquals(90, policy.normalizeScore(FeedbackResultType.CORRECT, 95));
        assertEquals(60, policy.normalizeScore(FeedbackResultType.DEFICIENT, 75));
        assertEquals(20, policy.normalizeScore(FeedbackResultType.WRONG, 40));
        assertEquals(0, policy.normalizeScore(FeedbackResultType.UNKNOWN, 30));
    }

    @Test
    void shouldNormalizeResultAndRejectUnknownForJudge() {
        assertEquals(FeedbackResultType.CORRECT, policy.normalizeResult("correct"));
        assertEquals(FeedbackResultType.DEFICIENT, policy.normalizeResult("unexpected"));
        assertTrue(policy.allowedForJudge(FeedbackResultType.CORRECT));
        assertTrue(policy.allowedForJudge(FeedbackResultType.DEFICIENT));
        assertTrue(policy.allowedForJudge(FeedbackResultType.WRONG));
        assertFalse(policy.allowedForJudge(FeedbackResultType.UNKNOWN));
    }
}
