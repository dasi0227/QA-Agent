package com.dasi.qa.agent.domain.identity.service.auth;

import com.dasi.qa.agent.domain.identity.model.enumeration.AccountStatus;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.*;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.dto.request.auth.LoginRequest;
import com.dasi.qa.agent.types.dto.request.auth.RefreshRequest;
import com.dasi.qa.agent.types.dto.request.auth.RegisterRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.auth.AuthResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Random;

@Service
public class AuthService implements IAuthService {

    private static final String VERIFY_CODE_FORMAT = "%06d";

    private final IIdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final IJwtUtil IJwtUtil;
    private final IContextUtil contextUtil;
    private final IOssUtil ossUtil;
    private final IRedisUtil redisUtil;
    private final IEmailUtil emailUtil;
    private final IIdUtil idUtil;

    @Value("${qa-agent.avatar.default-url:}")
    private String defaultAvatarUrl;

    public AuthService(IIdentityRepository identityRepository, PasswordEncoder passwordEncoder,
                       IJwtUtil IJwtUtil, IContextUtil contextUtil, IOssUtil ossUtil,
                       IRedisUtil redisUtil, IEmailUtil emailUtil,
                       IIdUtil idUtil) {
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.IJwtUtil = IJwtUtil;
        this.contextUtil = contextUtil;
        this.ossUtil = ossUtil;
        this.redisUtil = redisUtil;
        this.emailUtil = emailUtil;
        this.idUtil = idUtil;
    }

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (identityRepository.findUserAccountByUsername(request.getUsername()) != null) {
            throw new ApiException(ResultCode.USERNAME_CONFLICT);
        }
        if (identityRepository.findUserAccountByEmail(request.getEmail()) != null) {
            throw new ApiException(ResultCode.EMAIL_ALREADY_REGISTERED);
        }
        String codeKey = RedisConstant.AUTH_VERIFY_CODE_KEY + request.getEmail();
        String storedCode = redisUtil.get(codeKey);
        if (storedCode == null) {
            throw new ApiException(ResultCode.VERIFY_CODE_EXPIRED);
        }
        if (!storedCode.equals(request.getVerifyCode())) {
            throw new ApiException(ResultCode.VERIFY_CODE_INVALID);
        }
        redisUtil.delete(codeKey);
        UserAccountRequest accountRequest = new UserAccountRequest();
        accountRequest.setId(idUtil.nextId());
        accountRequest.setUsername(request.getUsername());
        accountRequest.setEmail(request.getEmail());
        accountRequest.setPassword(passwordEncoder.encode(request.getPassword()));
        accountRequest.setStatus(AccountStatus.ACTIVE.name());
        if (StringUtils.hasText(defaultAvatarUrl)) {
            accountRequest.setAvatar(defaultAvatarUrl);
        }
        UserAccountResponse created = identityRepository.createUserAccount(accountRequest, accountRequest.getId());
        UserProfileRequest profileRequest = new UserProfileRequest();
        profileRequest.setAllowReferMemory(false);
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
        if (redisUtil.get(rateLimitKey) != null) {
            throw new ApiException(ResultCode.VERIFY_CODE_RATE_LIMITED);
        }
        String code = String.format(VERIFY_CODE_FORMAT, new Random().nextInt(1000000));
        String codeKey = RedisConstant.AUTH_VERIFY_CODE_KEY + email;
        redisUtil.set(codeKey, code, 300);
        redisUtil.set(rateLimitKey, "1", 60);
        emailUtil.sendVerifyCode(email, code);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        UserAccountResponse account = identityRepository.findUserAccountByUsername(request.getUsername());
        if (account == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new ApiException(ResultCode.ACCOUNT_DISABLED, "账号当前不可用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.getPassword(), account.getPassword())) {
            throw new ApiException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return buildAuthResponse(account);
    }

    @Override
    public AuthResponse refresh(RefreshRequest request) {
        if (!IJwtUtil.isRefreshTokenValid(request.getRefreshToken())) {
            throw new ApiException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        String userId = IJwtUtil.parseUserId(request.getRefreshToken());
        UserAccountResponse account = identityRepository.detailUserAccount(userId, userId);
        if (!AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new ApiException(ResultCode.ACCOUNT_DISABLED, "账号当前不可用，请联系管理员");
        }
        return buildAuthResponse(account);
    }

    @Override
    public AuthResponse me() {
        String userId = contextUtil.getUserId();
        UserAccountResponse account = identityRepository.detailUserAccount(userId, userId);
        if (!AccountStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new ApiException(ResultCode.ACCOUNT_DISABLED, "账号当前不可用，请联系管理员");
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
                .avatar(ossUtil.getPublicUrl(account.getAvatar()))
                .accessToken(includeTokens ? IJwtUtil.generateAccessToken(account.getId()) : null)
                .refreshToken(includeTokens ? IJwtUtil.generateRefreshToken(account.getId()) : null)
                .build();
    }
}
