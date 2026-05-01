package com.dasi.qa.agent.types.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseRequest {

    private String id;

    @JsonIgnore
    private String userId;
}
