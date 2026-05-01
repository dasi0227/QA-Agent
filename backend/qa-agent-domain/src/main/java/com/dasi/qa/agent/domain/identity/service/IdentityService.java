package com.dasi.qa.agent.domain.identity.service;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.service.support.AbstractDomainServiceSupport;
import com.dasi.qa.agent.domain.util.UserContext;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.model.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class IdentityService extends AbstractDomainServiceSupport implements IIdentityService {

    private static final String USER_ACCOUNT_CACHE_NAME = "identity:user-account";
    private static final String USER_PROFILE_CACHE_NAME = "identity:user-profile";

    private final IIdentityRepository repository;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(IIdentityRepository repository, PasswordEncoder passwordEncoder, UserContext userContext) {
        super(userContext);
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Cacheable(cacheNames = USER_ACCOUNT_CACHE_NAME, key = "@cacheKeyBuilder.detail('identity:user-account', 'self', #id)")
    public UserAccountResponse detailUserAccount(String id) {
        return repository.detailUserAccount(id, id);
    }

    @Override
    @Cacheable(cacheNames = USER_ACCOUNT_CACHE_NAME, key = "@cacheKeyBuilder.query('identity:user-account', 'self', #request)")
    public List<UserAccountResponse> queryUserAccount(UserAccountRequest request) {
        return repository.queryUserAccount(request, request.getId());
    }

    @Override
    @CacheEvict(cacheNames = USER_ACCOUNT_CACHE_NAME, allEntries = true)
    public UserAccountResponse createUserAccount(UserAccountRequest request) {
        if (StrUtil.isBlank(request.getUsername()) || StrUtil.isBlank(request.getPassword())) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setStatus(StrUtil.isBlank(request.getStatus()) ? "ACTIVE" : request.getStatus());
        if (StrUtil.isNotBlank(request.getPassword())) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        return repository.createUserAccount(request, request.getId());
    }

    @Override
    @CacheEvict(cacheNames = USER_ACCOUNT_CACHE_NAME, allEntries = true)
    public UserAccountResponse updateUserAccount(UserAccountRequest request) {
        if (request.getId() == null || request.getId().isBlank()) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        if (StrUtil.isBlank(request.getPassword())) {
            request.setPassword(null);
        } else {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        request.setUpdatedAt(LocalDateTime.now());
        return repository.updateUserAccount(request, request.getId());
    }

    @Override
    @CacheEvict(cacheNames = USER_ACCOUNT_CACHE_NAME, allEntries = true)
    public void deleteUserAccount(String id) {
        repository.deleteUserAccount(id, id);
    }

    @Override
    @Cacheable(cacheNames = USER_PROFILE_CACHE_NAME, key = "@cacheKeyBuilder.detail('identity:user-profile', @userContextImpl.getUserId(), #id)")
    public UserProfileResponse detailUserProfile(String id) {
        return repository.detailUserProfile(currentUserId(), currentUserId());
    }

    @Override
    @Cacheable(cacheNames = USER_PROFILE_CACHE_NAME, key = "@cacheKeyBuilder.query('identity:user-profile', @userContextImpl.getUserId(), #request)")
    public List<UserProfileResponse> queryUserProfile(UserProfileRequest request) {
        request.setUserId(currentUserId());
        return repository.queryUserProfile(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = USER_PROFILE_CACHE_NAME, allEntries = true)
    public UserProfileResponse createUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        request.setId(userId);
        request.setUserId(userId);
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        return repository.createUserProfile(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = USER_PROFILE_CACHE_NAME, allEntries = true)
    public UserProfileResponse updateUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        request.setId(userId);
        request.setUserId(userId);
        request.setUpdatedAt(LocalDateTime.now());
        return repository.updateUserProfile(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = USER_PROFILE_CACHE_NAME, allEntries = true)
    public void deleteUserProfile(String id) {
        repository.deleteUserProfile(currentUserId(), currentUserId());
    }
}
