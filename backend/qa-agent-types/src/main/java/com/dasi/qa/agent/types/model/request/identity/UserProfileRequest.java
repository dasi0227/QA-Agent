package com.dasi.qa.agent.types.model.request.identity;

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
public class UserProfileRequest extends BaseRequest {

    private String targetRole;
    private String targetDomain;
    private String targetCompany;
    private Boolean allowGeneralKnowledge;
    private Boolean allowWebSearch;
    private String answerStyle;
    private String feedbackStyle;
    private String age;
    private String grade;
    private String major;
    private String stage;
}
