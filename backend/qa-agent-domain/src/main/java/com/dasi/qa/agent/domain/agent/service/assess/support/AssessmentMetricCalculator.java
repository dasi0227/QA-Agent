package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItem;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class AssessmentMetricCalculator {

    public AssessMetrics calculate(AssessContext context) {
        validate(context);
        List<AssessItem> items = context.getItems();
        int correct = 0;
        int deficient = 0;
        int wrong = 0;
        int unknown = 0;
        int totalScore = 0;
        for (AssessItem item : items) {
            FeedbackResultType resultType = resultType(item.getResult());
            totalScore += item.getScore();
            switch (resultType) {
                case CORRECT -> correct++;
                case DEFICIENT -> deficient++;
                case WRONG -> wrong++;
                case UNKNOWN -> unknown++;
            }
        }
        int total = items.size();
        int score = Math.round((float) totalScore / total);
        BigDecimal accuracy = BigDecimal.valueOf(correct + deficient)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return AssessMetrics.builder()
                .totalQuestions(total)
                .score(score)
                .accuracy(accuracy)
                .correctCount(correct)
                .deficientCount(deficient)
                .wrongCount(wrong)
                .unknownCount(unknown)
                .build();
    }

    public void validate(AssessContext context) {
        if (context == null || context.getItems() == null || context.getItems().isEmpty()) {
            throw notCompleted("练习题目为空，不能生成整轮评估");
        }
        int totalQuestions = context.getTotalQuestions() == null ? 0 : context.getTotalQuestions();
        if (context.getItems().size() != totalQuestions) {
            throw notCompleted("练习题目数量异常，不能生成整轮评估");
        }
        for (AssessItem item : context.getItems()) {
            if (item.getAnsweredAt() == null || item.getResult() == null || item.getResult().isBlank() || item.getScore() == null) {
                throw notCompleted("练习尚未完成，不能生成整轮评估");
            }
            resultType(item.getResult());
        }
    }

    private FeedbackResultType resultType(String value) {
        try {
            return FeedbackResultType.valueOf(value.trim().toUpperCase());
        } catch (Exception exception) {
            throw notCompleted("练习题目结果不完整，不能生成整轮评估");
        }
    }

    private ApiException notCompleted(String message) {
        return new ApiException(ResultCode.PRACTICE_SESSION_NOT_COMPLETED.getCode(), message);
    }
}
