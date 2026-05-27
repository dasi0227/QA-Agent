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
public class UserMemoryResponse extends BaseResponse {

    private String memoryType;
    private String targetType;
    private String targetKey;
    private String summary;
    private String content;
    private Integer supportCount;
    private String status;
    private String firstSeenAt;
    private String lastSeenAt;
    private String hiddenAt;
    private String latestSessionId;
    private String latestQaSetId;
}
