package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RecordAgent 输出的单条内部记忆线索。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryClueResult {

    private String type;
    private String observation;
    private String importance;
}
