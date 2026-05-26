package com.dasi.qa.agent.types.dto.request.identity;

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
public class UserProfileRequest extends BaseRequest {

    private String targetRole;
    private String targetDomain;
    private String targetCompany;
    private Boolean allowReferMemory;
    private Boolean allowWebSearch;
    private Boolean allowFallback;
    private String answerStyle;
    private String feedbackStyle;
    private String grade;
    private String major;
    private String stage;
    private String llmBaseUrl;
    private String llmApiKey;
    private String llmModelName;
}
