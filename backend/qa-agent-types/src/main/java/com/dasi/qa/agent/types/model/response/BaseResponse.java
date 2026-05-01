package com.dasi.qa.agent.types.model.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse {

    private String id;

    @JsonIgnore
    private String userId;

    private String createdAt;

    private String updatedAt;
}
