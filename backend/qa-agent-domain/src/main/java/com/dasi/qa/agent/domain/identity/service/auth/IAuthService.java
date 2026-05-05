package com.dasi.qa.agent.domain.identity.service.auth;

import com.dasi.qa.agent.types.dto.request.auth.LoginRequest;
import com.dasi.qa.agent.types.dto.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.dto.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.dto.response.auth.AuthResponse;

public interface IAuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);

    AuthResponse me();

    void sendVerifyCode(String email);
}
