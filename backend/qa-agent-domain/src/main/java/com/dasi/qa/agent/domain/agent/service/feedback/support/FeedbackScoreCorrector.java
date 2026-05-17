package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration.FeedbackResult;
import com.dasi.qa.agent.domain.agent.service.feedback.model.result.JudgeResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * FeedbackScoreCorrector 负责约束单题反馈结果和离散分数。
 */
@Component
public class FeedbackScoreCorrector {

    private static final Map<FeedbackResult, Set<Integer>> ALLOWED_SCORES = Map.of(
            FeedbackResult.PERFECT, Set.of(100),
            FeedbackResult.CORRECT, Set.of(80, 90),
            FeedbackResult.DEFICIENT, Set.of(50, 60, 70),
            FeedbackResult.WRONG, Set.of(0, 10, 20, 30, 40),
            FeedbackResult.UNKNOWN, Set.of(0)
    );

    private static final Map<FeedbackResult, Integer> DEFAULT_SCORES = Map.of(
            FeedbackResult.PERFECT, 100,
            FeedbackResult.CORRECT, 90,
            FeedbackResult.DEFICIENT, 60,
            FeedbackResult.WRONG, 20,
            FeedbackResult.UNKNOWN, 0
    );

    /**
     * 对 JudgeAgent 输出做归一化、合法性校验和分数校准，直接修改传入对象。
     */
    public void correct(JudgeResult judgeResult) {
        FeedbackResult resultType = FeedbackResult.fromValue(judgeResult.getResult());
        judgeResult.setResult(resultType.name());
        Integer score = judgeResult.getScore();
        judgeResult.setScore(score != null && ALLOWED_SCORES.get(resultType).contains(score) ? score : DEFAULT_SCORES.get(resultType));
    }

}
