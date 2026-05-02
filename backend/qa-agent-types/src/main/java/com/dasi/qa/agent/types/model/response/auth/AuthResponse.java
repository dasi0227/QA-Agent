package com.dasi.qa.agent.types.model.response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String userId;

    private String username;

    private String email;

    private String status;

    private Boolean profileCompleted;

    private String accessToken;

    private String refreshToken;
}
