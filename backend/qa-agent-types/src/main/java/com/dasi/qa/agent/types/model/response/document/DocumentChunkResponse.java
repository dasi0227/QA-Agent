package com.dasi.qa.agent.types.model.response.document;

import com.dasi.qa.agent.types.model.response.BaseResponse;
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
    private Integer chunkIndex;
    private String titlePath;
    private String content;
    private String summary;
    private String moduleTagsJson;
    private String embeddingVector;
}
