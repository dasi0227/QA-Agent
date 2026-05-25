package com.dasi.qa.agent.domain.agent.service.memory.support;

import com.dasi.qa.agent.domain.agent.service.memory.model.result.MemoryCandidateResult;
import com.dasi.qa.agent.domain.memory.model.enumeration.MemoryBehaviorKey;
import com.dasi.qa.agent.domain.memory.model.enumeration.MemoryTargetType;
import com.dasi.qa.agent.domain.memory.model.enumeration.MemoryProficientType;
import com.dasi.qa.agent.types.constant.ModuleTag;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class MemoryResultCleaner {

    private static final int MAX_CANDIDATES = 5;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_SUMMARY_LENGTH = 240;
    private static final int MAX_DETAIL_LENGTH = 1200;

    public List<MemoryCandidateResult> clean(List<MemoryCandidateResult> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<MemoryCandidateResult> results = new ArrayList<>();
        for (MemoryCandidateResult value : values) {
            MemoryCandidateResult cleaned = cleanOne(value);
            if (cleaned == null) {
                continue;
            }
            results.add(cleaned);
            if (results.size() == MAX_CANDIDATES) {
                break;
            }
        }
        return results;
    }

    private MemoryCandidateResult cleanOne(MemoryCandidateResult value) {
        if (value == null || !StringUtils.hasText(value.getTitle()) || !StringUtils.hasText(value.getSummary())) {
            return null;
        }
        MemoryProficientType memoryProficientType = MemoryProficientType.fromValue(value.getMemoryType());
        MemoryTargetType targetType = MemoryTargetType.fromValue(value.getTargetType());
        if (memoryProficientType == null || targetType == null) {
            return null;
        }
        String targetKey = cleanTargetKey(targetType, value.getTargetKey());
        if (!StringUtils.hasText(targetKey)) {
            return null;
        }
        List<String> evidenceRefs = cleanEvidenceRefs(value.getEvidenceRefs());
        if (evidenceRefs.isEmpty()) {
            return null;
        }
        return MemoryCandidateResult.builder()
                .memoryType(memoryProficientType.name())
                .targetType(targetType.name())
                .targetKey(targetKey)
                .title(limit(value.getTitle(), MAX_TITLE_LENGTH))
                .summary(limit(value.getSummary(), MAX_SUMMARY_LENGTH))
                .detail(limit(value.getDetail(), MAX_DETAIL_LENGTH))
                .evidenceRefs(evidenceRefs)
                .relatedMemoryId(StringUtils.hasText(value.getRelatedMemoryId()) ? value.getRelatedMemoryId().trim() : "")
                .build();
    }

    private String cleanTargetKey(MemoryTargetType targetType, String targetKey) {
        if (!StringUtils.hasText(targetKey)) {
            return "";
        }
        String value = targetKey.trim();
        return switch (targetType) {
            case MODULE -> ModuleTag.contains(value) ? value : "";
            case BEHAVIOR -> MemoryBehaviorKey.fromValue(value) == null ? "" : MemoryBehaviorKey.fromValue(value).name();
            case GENERAL -> "GENERAL".equalsIgnoreCase(value) ? "GENERAL" : "";
        };
    }

    private List<String> cleanEvidenceRefs(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (String ref : refs) {
            if (StringUtils.hasText(ref)) {
                results.add(ref.trim());
            }
        }
        return List.copyOf(results);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
