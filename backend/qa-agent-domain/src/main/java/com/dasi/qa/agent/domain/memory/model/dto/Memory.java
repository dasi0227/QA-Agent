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
public class Memory {

    private String id;
    private String userId;
    private String memoryType;
    private String targetType;
    private String targetKey;
    private String title;
    private String summary;
    private String detail;
    private Integer supportCount;
    private String status;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    private LocalDateTime hiddenAt;
    private String latestSessionId;
    private String latestQaSetId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
