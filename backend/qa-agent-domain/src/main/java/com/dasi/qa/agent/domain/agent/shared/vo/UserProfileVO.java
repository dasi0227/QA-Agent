package com.dasi.qa.agent.domain.agent.shared.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO {

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
