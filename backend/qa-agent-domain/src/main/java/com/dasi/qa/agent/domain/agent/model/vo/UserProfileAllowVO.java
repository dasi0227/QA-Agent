package com.dasi.qa.agent.domain.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileAllowVO {

    private Boolean allowGeneralKnowledge;

    private Boolean allowWebSearch;

    private Boolean allowFallback;

}
