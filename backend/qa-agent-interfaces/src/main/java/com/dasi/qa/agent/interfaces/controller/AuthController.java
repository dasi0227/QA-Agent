package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.identity.service.IAuthService;
import com.dasi.qa.agent.types.model.request.auth.LoginRequest;
import com.dasi.qa.agent.types.model.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.model.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.model.response.auth.AuthResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request));
    }
}
