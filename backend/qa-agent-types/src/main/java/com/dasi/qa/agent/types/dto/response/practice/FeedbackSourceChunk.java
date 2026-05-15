package com.dasi.qa.agent.types.dto.response.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackSourceChunk {

    private String chunkId;
    private String documentId;
    private String titlePath;
    private String summary;
    private String content;
}
