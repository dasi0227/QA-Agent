package com.dasi.qa.agent.domain.agent.service.memory.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeResult {

    private String summary;
    private String content;
}
