package com.dasi.qa.agent.types.dto.response.document;

import com.dasi.qa.agent.types.enumeration.SearchStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String chunkId;

    private String documentId;

    private String titlePath;

    private String content;

    private String summary;

    private List<String> moduleTags;

    private float score;

    private float vectorScore;

    private float keywordScore;

    private SearchStrategy source;
}
