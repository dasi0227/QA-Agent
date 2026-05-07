package com.dasi.qa.agent.domain.agent.shared.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLlmModelVO {

    private String baseUrl;

    private String apiKey;

    private String modelName;

}
