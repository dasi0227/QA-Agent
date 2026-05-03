package com.dasi.qa.agent.domain.identity.service.auth;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.IAliOssUtil;
import com.dasi.qa.agent.domain.util.IEmailUtil;
import com.dasi.qa.agent.domain.util.JwtUtil;
import com.dasi.qa.agent.domain.util.UserContextUtil;
import com.dasi.qa.agent.types.constant.RedisConstant;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

@Service
public class AuthService implements IAuthService {

    private final IIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserContextUtil userContext;
    private final IAliOssUtil aliOssUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final IEmailUtil emailUtil;

    @Value("${qa-agent.avatar.default-url:}")
    private String defaultAvatarUrl;

    public AuthService(IIdentityRepository identityRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, UserContextUtil userContext, IAliOssUtil aliOssUtil,
                       StringRedisTemplate stringRedisTemplate, IEmailUtil emailUtil) {
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userContext = userContext;
        this.aliOssUtil = aliOssUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.emailUtil = emailUtil;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (identityRepository.findUserAccountByUsername(request.getUsername()) != null) {
            throw new ApiException(ResultCode.CONFLICT);
        }
        if (identityRepository.findUserAccountByEmail(request.getEmail()) != null) {
            throw new ApiException(ResultCode.EMAIL_ALREADY_REGISTERED);
        }
        String codeKey = RedisConstant.AUTH_VERIFY_CODE_KEY + request.getEmail();
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (storedCode == null) {
            throw new ApiException(ResultCode.VERIFY_CODE_EXPIRED);
        }
        if (!storedCode.equals(request.getVerifyCode())) {
            throw new ApiException(ResultCode.VERIFY_CODE_INVALID);
        }
        stringRedisTemplate.delete(codeKey);
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
        UserProfileRequest profileRequest = new UserProfileRequest();
        profileRequest.setAllowGeneralKnowledge(false);
        profileRequest.setAllowWebSearch(false);
        profileRequest.setAnswerStyle("先结论后展开，优先说明原理、场景和风险");
        profileRequest.setFeedbackStyle("先指出问题，再补正确答案和追问点");
        identityRepository.createUserProfile(profileRequest, accountRequest.getId());
        return buildAuthResponse(created);
    }

    @Override
    public void sendVerifyCode(String email) {
        if (identityRepository.findUserAccountByEmail(email) != null) {
            throw new ApiException(ResultCode.EMAIL_ALREADY_REGISTERED);
        }
        String rateLimitKey = RedisConstant.AUTH_VERIFY_RATE_LIMIT_KEY + email;
        if (stringRedisTemplate.opsForValue().get(rateLimitKey) != null) {
            throw new ApiException(ResultCode.VERIFY_CODE_RATE_LIMITED);
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        String codeKey = RedisConstant.AUTH_VERIFY_CODE_KEY + email;
        stringRedisTemplate.opsForValue().set(codeKey, code, Duration.ofMinutes(5));
        stringRedisTemplate.opsForValue().set(rateLimitKey, "1", Duration.ofSeconds(60));
        emailUtil.sendVerifyCode(email, code);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
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
