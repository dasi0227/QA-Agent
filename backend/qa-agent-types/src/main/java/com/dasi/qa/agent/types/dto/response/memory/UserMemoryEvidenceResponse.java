package com.dasi.qa.agent.types.dto.response.memory;

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
public class UserMemoryEvidenceResponse extends BaseResponse {

    private String memoryId;
    private String sessionId;
    private String sessionItemId;
    private String qaSetId;
    private String qaItemId;
    private String moduleTag;
    private String questionSnapshot;
    private String result;
    private Integer score;
    private String sourceChunkIdsJson;
    private String evidenceSummary;
}
