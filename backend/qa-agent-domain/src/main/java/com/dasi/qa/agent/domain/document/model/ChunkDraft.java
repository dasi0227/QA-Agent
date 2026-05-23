package com.dasi.qa.agent.domain.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDraft {

    private String chunkId;
    private int chunkIndex;
    private String headingPath;
    private String content;
    private String summary;

}
