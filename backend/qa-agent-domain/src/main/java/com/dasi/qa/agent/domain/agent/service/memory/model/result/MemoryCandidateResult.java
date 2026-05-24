package com.dasi.qa.agent.domain.agent.service.memory.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryCandidateResult {

    private String memoryType;
    private String targetType;
    private String targetKey;
    private String title;
    private String summary;
    private String detail;
    private List<String> evidenceRefs;
    private String relatedMemoryId;
    private String confidenceHint;
}
