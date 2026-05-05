package com.dasi.qa.agent.types.dto.response.identity;

import com.dasi.qa.agent.types.dto.response.BaseResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class UserAccountResponse extends BaseResponse {

    private String username;

    private String email;

    @JsonIgnore
    private String password;

    private String status;

    private String avatar;
}
