package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RecordAgent 输出结果，根数组解析后封装为 clues。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordResult {

    private List<MemoryClueResult> clues;
}
