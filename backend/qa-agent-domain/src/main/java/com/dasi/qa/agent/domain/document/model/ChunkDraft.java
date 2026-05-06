package com.dasi.qa.agent.domain.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDraft {

    private String chunkId;
    private int chunkIndex;
    private String titlePath;
    private String content;
    private List<String> moduleTags;
    private String summary;

}
