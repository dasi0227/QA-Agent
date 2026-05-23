package com.dasi.qa.agent.types.dto.response.document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String chunkId;

    private String documentId;

    private String headingPath;

    private String content;

    private String summary;

    private float score;

    private float vectorScore;

    private float keywordScore;
}
