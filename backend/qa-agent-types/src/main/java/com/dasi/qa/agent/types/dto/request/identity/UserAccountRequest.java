package com.dasi.qa.agent.types.dto.request.identity;

import com.dasi.qa.agent.types.dto.request.BaseRequest;
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
public class UserAccountRequest extends BaseRequest {

    private String username;

    private String email;

    private String password;

    private String status;

    private String avatar;
}
