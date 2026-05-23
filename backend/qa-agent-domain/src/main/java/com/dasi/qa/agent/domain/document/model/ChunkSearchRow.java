package com.dasi.qa.agent.domain.document.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSearchRow {

    private String chunkId;
    private String documentId;
    private String userId;
    private int chunkIndex;
    private String headingPath;
    private String content;
    private String summary;
    private float[] embedding;
    private float vectorScore;
    private float keywordScore;

}
