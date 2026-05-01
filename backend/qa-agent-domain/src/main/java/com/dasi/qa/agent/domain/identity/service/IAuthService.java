package com.dasi.qa.agent.domain.identity.service;

import com.dasi.qa.agent.types.model.request.auth.LoginRequest;
import com.dasi.qa.agent.types.model.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.model.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.model.response.auth.AuthResponse;

public interface IAuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshRequest request);
}
