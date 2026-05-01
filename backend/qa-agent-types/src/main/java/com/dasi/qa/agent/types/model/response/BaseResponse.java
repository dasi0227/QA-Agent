package com.dasi.qa.agent.types.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse {

    private String id;

    private String createdAt;

    private String updatedAt;
}
