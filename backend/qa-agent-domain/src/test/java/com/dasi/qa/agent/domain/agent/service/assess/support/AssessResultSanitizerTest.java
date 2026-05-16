package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.DiagnosisResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.MemoryClueResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.result.StrengthResult;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssessResultSanitizerTest {

    private final AssessResultSanitizer sanitizer = new AssessResultSanitizer(new TestJsonUtil());

    @Test
    void shouldLimitDiagnosisPoints() {
        DiagnosisResult result = sanitizer.sanitizeDiagnosis(DiagnosisResult.builder()
                .strengths(List.of(point("A"), point("B"), point("C"), point("D")))
                .build());

        assertEquals(3, result.getStrengths().size());
    }

    @Test
    void shouldDropInvalidMemoryClueTypeAndNormalizeImportance() {
        RecordResult result = sanitizer.sanitizeRecord(RecordResult.builder()
                .clues(List.of(
                        MemoryClueResult.builder()
                                .type("CONCEPT_WEAKNESS")
                                .observation("用户对代理边界不稳定。")
                                .importance("bad")
                                .build(),
                        MemoryClueResult.builder()
                                .type("BAD_TYPE")
                                .observation("无效类型")
                                .importance("HIGH")
                                .build()
                ))
                .build());

        assertEquals(1, result.getClues().size());
        assertEquals("MEDIUM", result.getClues().get(0).getImportance());
    }

    @Test
    void shouldFallbackInvalidJson() {
        DiagnosisResult diagnosis = sanitizer.parseDiagnosis("not-json");
        RecordResult record = sanitizer.parseRecord("not-json");

        assertTrue(diagnosis.getStrengths().isEmpty());
        assertTrue(record.getClues().isEmpty());
    }

    @Test
    void shouldFallbackBlankAdvice() {
        String response = "{\"overallComment\":\"\",\"reviewGuidance\":\"\"}";

        assertEquals("本轮练习已完成，系统根据单题结果计算出总分 75，达标率 80.00%。",
                sanitizer.parseAdvice(response, metrics()).getOverallComment());
    }

    private StrengthResult point(String title) {
        return StrengthResult.builder()
                .title(title)
                .analysis("analysis")
                .build();
    }

    private AssessMetrics metrics() {
        return AssessMetrics.builder()
                .score(75)
                .accuracy(new BigDecimal("80.00"))
                .build();
    }

    private static class TestJsonUtil implements IJsonUtil {

        @Override
        public String toJsonString(Object obj) {
            return JSON.toJSONString(obj);
        }

        @Override
        public String extractJsonArray(String json) {
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            return start >= 0 && end > start ? json.substring(start, end + 1) : json;
        }

        @Override
        public String extractJsonObject(String json) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            return start >= 0 && end > start ? json.substring(start, end + 1) : json;
        }

        @Override
        public <T> List<T> parseJsonArray(String rawJson, Class<T> clazz) {
            return JSON.parseArray(extractJsonArray(rawJson), clazz);
        }

        @Override
        public <T> T parseJsonObject(String rawJson, Class<T> clazz) {
            return JSON.parseObject(extractJsonObject(rawJson), clazz);
        }
    }
}
