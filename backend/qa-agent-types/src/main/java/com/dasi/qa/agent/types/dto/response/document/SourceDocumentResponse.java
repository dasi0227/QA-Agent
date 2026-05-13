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
public class SourceDocumentResponse extends BaseResponse {

    private String fileName;
    private String fileType;
    private String filePath;
    private String rawContent;
    private String summary;
    private Integer referenceCount;
    private Boolean deleted;
}
