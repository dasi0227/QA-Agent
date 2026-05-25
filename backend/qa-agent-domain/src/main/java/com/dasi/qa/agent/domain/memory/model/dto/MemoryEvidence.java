package com.dasi.qa.agent.domain.memory.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEvidence {

    private String id;
    private String memoryId;
    private String userId;
    private String sessionId;
    private String sessionItemId;
    private String qaSetId;
    private String qaItemId;
    private String moduleTag;
    private String questionSnapshot;
    private String result;
    private Integer score;
    private String sourceChunkIdsJson;
    private String memoryClueJson;
    private String evidenceSummary;
    private LocalDateTime createdAt;
}
