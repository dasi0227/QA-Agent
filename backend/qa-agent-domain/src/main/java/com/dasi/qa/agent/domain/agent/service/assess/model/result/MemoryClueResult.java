package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryClueResult {

    private String type;
    private String observation;
    private String importance;
}
