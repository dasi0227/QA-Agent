package com.dasi.qa.agent.domain.identity.service;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.JwtUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.auth.LoginRequest;
import com.dasi.qa.agent.types.model.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.model.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.response.auth.AuthResponse;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService implements IAuthService {

    private final IIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(IIdentityRepository identityRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (StrUtil.isBlank(request.getUsername()) || StrUtil.isBlank(request.getPassword())) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        if (identityRepository.findUserAccountByUsername(request.getUsername()) != null) {
            throw new ApiException(ResultCode.CONFLICT);
        }
        if (StrUtil.isNotBlank(request.getEmail()) && identityRepository.findUserAccountByEmail(request.getEmail()) != null) {
            throw new ApiException(ResultCode.CONFLICT);
        }
        UserAccountRequest accountRequest = new UserAccountRequest();
        accountRequest.setId(UUID.randomUUID().toString());
        accountRequest.setUsername(request.getUsername());
        accountRequest.setEmail(request.getEmail());
        accountRequest.setPassword(passwordEncoder.encode(request.getPassword()));
        accountRequest.setStatus("ACTIVE");
        accountRequest.setCreatedAt(LocalDateTime.now());
        accountRequest.setUpdatedAt(LocalDateTime.now());
        UserAccountResponse created = identityRepository.createUserAccount(accountRequest, accountRequest.getId());
        return buildAuthResponse(created);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        if (StrUtil.isBlank(request.getUsername()) || StrUtil.isBlank(request.getPassword())) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        UserAccountResponse account = identityRepository.findUserAccountByUsername(request.getUsername());
        if (account == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return buildAuthResponse(account);
    }

    @Override
    public AuthResponse refresh(RefreshRequest request) {
        if (!jwtUtil.isRefreshTokenValid(request.getRefreshToken())) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        String userId = jwtUtil.parseUserId(request.getRefreshToken());
        UserAccountResponse account = identityRepository.detailUserAccount(userId, userId);
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return buildAuthResponse(account);
    }

    private AuthResponse buildAuthResponse(UserAccountResponse account) {
        return AuthResponse.builder()
                .userId(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .accessToken(jwtUtil.generateAccessToken(account.getId()))
                .refreshToken(jwtUtil.generateRefreshToken(account.getId()))
                .build();
    }
}
