package com.dasi.qa.agent.types.model.request.document;

import com.dasi.qa.agent.types.model.request.BaseRequest;
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
public class SourceDocumentRequest extends BaseRequest {

    private String fileName;
    private String fileType;
    private String filePath;
    private String rawContent;
    private String normalizedContent;
    private String summary;
    private String moduleTagsJson;
    private Integer referenceCount;
    private Boolean deleted;
}
