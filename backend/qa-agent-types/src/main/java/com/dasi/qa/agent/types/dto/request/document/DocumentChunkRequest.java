package com.dasi.qa.agent.types.dto.request.document;

import com.dasi.qa.agent.types.dto.request.BaseRequest;
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
public class DocumentChunkRequest extends BaseRequest {

    private String documentId;
    private Integer chunkIndex;
    private String headingPath;
    private String content;
    private String summary;
    private String embeddingVector;
}
