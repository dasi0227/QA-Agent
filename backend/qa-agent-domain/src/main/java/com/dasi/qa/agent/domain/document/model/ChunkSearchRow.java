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
public class ChunkSearchRow {

    private String chunkId;
    private String documentId;
    private String userId;
    private int chunkIndex;
    private String titlePath;
    private String content;
    private String summary;
    private List<String> moduleTags;
    private float[] embedding;
    private float vectorScore;
    private float keywordScore;

}
