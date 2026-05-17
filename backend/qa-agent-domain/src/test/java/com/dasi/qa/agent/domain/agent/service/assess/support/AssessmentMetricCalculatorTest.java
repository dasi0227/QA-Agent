package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessContext;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItem;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssessmentMetricCalculatorTest {

    private final AssessmentMetricCalculator calculator = new AssessmentMetricCalculator();

    @Test
    void shouldCalculateScoreAccuracyAndCounts() {
        AssessContext context = AssessContext.builder()
                .totalQuestions(4)
                .items(List.of(
                        item("CORRECT", 90),
                        item("DEFICIENT", 60),
                        item("WRONG", 20),
                        item("UNKNOWN", 0)
                ))
                .build();

        AssessMetrics metrics = calculator.calculate(context);

        assertEquals(43, metrics.getScore());
        assertEquals(new BigDecimal("50.00"), metrics.getAccuracy());
        assertEquals(1, metrics.getCorrectCount());
        assertEquals(1, metrics.getDeficientCount());
        assertEquals(1, metrics.getWrongCount());
        assertEquals(1, metrics.getUnknownCount());
    }

    @Test
    void shouldRejectUnfinishedSession() {
        AssessContext context = AssessContext.builder()
                .totalQuestions(1)
                .items(List.of(AssessItem.builder()
                        .result("CORRECT")
                        .score(90)
                        .build()))
                .build();

        ApiException exception = assertThrows(ApiException.class, () -> calculator.calculate(context));

        assertEquals(ResultCode.PRACTICE_SESSION_NOT_COMPLETED.getCode(), exception.getCode());
    }

    @Test
    void shouldRejectMismatchedItemCount() {
        AssessContext context = AssessContext.builder()
                .totalQuestions(2)
                .items(List.of(item("CORRECT", 90)))
                .build();

        ApiException exception = assertThrows(ApiException.class, () -> calculator.calculate(context));

        assertEquals(ResultCode.PRACTICE_SESSION_NOT_COMPLETED.getCode(), exception.getCode());
    }

    private AssessItem item(String result, Integer score) {
        return AssessItem.builder()
                .result(result)
                .score(score)
                .answeredAt(LocalDateTime.now())
                .build();
    }
}
