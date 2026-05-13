package com.dasi.qa.agent.types.dto.response.identity;

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
public class UserProfileResponse extends BaseResponse {

    private String targetRole;
    private String targetDomain;
    private String targetCompany;
    private Boolean allowGeneralKnowledge;
    private Boolean allowWebSearch;
    private String answerStyle;
    private String feedbackStyle;
    private String grade;
    private String major;
    private String stage;
    private String llmBaseUrl;
    private String llmApiKey;
    private String llmModelName;
}
