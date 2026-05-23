package com.dasi.qa.agent.types.dto.response.document;

import com.dasi.qa.agent.types.dto.response.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DocumentChunkResponse extends BaseResponse {

    private String documentId;
    private String fileName;
    private Integer chunkIndex;
    private String headingPath;
    private String content;
    private String summary;
    private String embeddingVector;
}
