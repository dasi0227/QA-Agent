package com.dasi.qa.agent.domain.agent.shared.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileInfoVO {

    private String targetRole;

    private String targetDomain;

    private String targetCompany;

    private String major;

    private String grade;

    private String stage;

}
