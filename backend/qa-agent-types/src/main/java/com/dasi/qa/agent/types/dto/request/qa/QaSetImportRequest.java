package com.dasi.qa.agent.types.dto.request.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaSetImportRequest {

    private String fileName;

    private byte[] content;
}
