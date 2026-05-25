package com.dasi.qa.agent.domain.agent.service.memory.support;

import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.ProficientType;
import com.dasi.qa.agent.domain.agent.service.memory.model.result.InvestResult;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.BehaviorKey;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.TargetType;
import com.dasi.qa.agent.types.constant.ModuleTag;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class MemoryResultCleaner {

    private static final int MAX_CANDIDATES = 5;

    public List<InvestResult> clean(List<InvestResult> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<InvestResult> results = new ArrayList<>();
        for (InvestResult value : values) {
            InvestResult cleaned = cleanOne(value);
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

    private InvestResult cleanOne(InvestResult value) {
        if (value == null || !StringUtils.hasText(value.getContent())) {
            return null;
        }
        ProficientType proficientType = ProficientType.fromValue(value.getMemoryType());
        TargetType targetType = TargetType.fromValue(value.getTargetType());
        if (proficientType == null || targetType == null) {
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
        return InvestResult.builder()
                .memoryType(proficientType.name())
                .targetType(targetType.name())
                .targetKey(targetKey)
                .content(value.getContent().trim())
                .evidenceRefs(evidenceRefs)
                .build();
    }

    private String cleanTargetKey(TargetType targetType, String targetKey) {
        if (!StringUtils.hasText(targetKey)) {
            return "";
        }
        String value = targetKey.trim();
        return switch (targetType) {
            case MODULE -> ModuleTag.contains(value) ? value : "";
            case BEHAVIOR -> BehaviorKey.fromValue(value) == null ? "" : BehaviorKey.fromValue(value).name();
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
}
