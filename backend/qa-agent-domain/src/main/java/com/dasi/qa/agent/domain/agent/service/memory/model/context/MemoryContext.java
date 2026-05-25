package com.dasi.qa.agent.domain.agent.service.memory.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryContext {

    private String sessionId;
    private String qaSetTitle;
    private String statsJson;
    private String itemsJson;
    private String existingMemoriesJson;
}
