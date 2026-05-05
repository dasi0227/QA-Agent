package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.identity.service.auth.IAuthService;
import com.dasi.qa.agent.types.dto.request.auth.LoginRequest;
import com.dasi.qa.agent.types.dto.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.dto.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.dto.request.auth.SendVerifyCodeRequest;
import com.dasi.qa.agent.types.dto.response.auth.AuthResponse;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/qa-agent/api/v1/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request));
    }

    @PostMapping("/send-verify-code")
    public Result<Void> sendVerifyCode(@Valid @RequestBody SendVerifyCodeRequest request) {
        authService.sendVerifyCode(request.getEmail());
        return Result.success();
    }


    @GetMapping("/me")
    public Result<AuthResponse> me() {
        return Result.success(authService.me());
    }
}
