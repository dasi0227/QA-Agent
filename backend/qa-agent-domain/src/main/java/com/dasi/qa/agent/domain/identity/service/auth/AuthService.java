package com.dasi.qa.agent.domain.identity.service.auth;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.IAliOssUtil;
import com.dasi.qa.agent.domain.util.JwtUtil;
import com.dasi.qa.agent.domain.util.UserContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.auth.LoginRequest;
import com.dasi.qa.agent.types.model.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.model.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.model.response.auth.AuthResponse;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService implements IAuthService {

    private final IIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserContextUtil userContext;
    private final IAliOssUtil aliOssUtil;

    @Value("${qa-agent.avatar.default-url:}")
    private String defaultAvatarUrl;

    public AuthService(IIdentityRepository identityRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, UserContextUtil userContext, IAliOssUtil aliOssUtil) {
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userContext = userContext;
        this.aliOssUtil = aliOssUtil;
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
        if (StrUtil.isNotBlank(defaultAvatarUrl)) {
            accountRequest.setAvatar(defaultAvatarUrl);
        }
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

    @Override
    public AuthResponse me() {
        String userId = userContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        UserAccountResponse account = identityRepository.detailUserAccount(userId, userId);
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return buildAuthResponse(account, false);
    }

    private AuthResponse buildAuthResponse(UserAccountResponse account) {
        return buildAuthResponse(account, true);
    }

    private AuthResponse buildAuthResponse(UserAccountResponse account, boolean includeTokens) {
        boolean profileCompleted = !identityRepository.queryUserProfile(new UserProfileRequest(), account.getId()).isEmpty();
        return AuthResponse.builder()
                .userId(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .status(account.getStatus())
                .profileCompleted(profileCompleted)
                .avatar(aliOssUtil.getPublicUrl(account.getAvatar()))
                .accessToken(includeTokens ? jwtUtil.generateAccessToken(account.getId()) : null)
                .refreshToken(includeTokens ? jwtUtil.generateRefreshToken(account.getId()) : null)
                .build();
    }
}
