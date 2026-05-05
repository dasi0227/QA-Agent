package com.dasi.qa.agent.domain.identity.service.crud;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.enumeration.AccountStatus;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProfileCrudService implements IProfileCrudService {

    private final IIdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IContextUtil contextUtil;

    public ProfileCrudService(IIdentityRepository repository, PasswordEncoder passwordEncoder, IContextUtil contextUtil) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.contextUtil = contextUtil;
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_ACCOUNT_DETAIL_KEY, 'self', #id)"
    )
    public UserAccountResponse detailUserAccount(String id) {
        return repository.detailUserAccount(id, id);
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_ACCOUNT_QUERY_KEY, 'self', #request)"
    )
    public List<UserAccountResponse> queryUserAccount(UserAccountRequest request) {
        return repository.queryUserAccount(request, request.getId());
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    public UserAccountResponse createUserAccount(UserAccountRequest request) {
        if (StrUtil.isBlank(request.getUsername()) || StrUtil.isBlank(request.getPassword())) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setStatus(StrUtil.isBlank(request.getStatus()) ? AccountStatus.ACTIVE.name() : request.getStatus());
        if (StrUtil.isNotBlank(request.getPassword())) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return repository.createUserAccount(request, request.getId());
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    public UserAccountResponse updateUserAccount(UserAccountRequest request) {
        if (request.getId() == null || request.getId().isBlank()) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        if (StrUtil.isBlank(request.getPassword())) {
            request.setPassword(null);
        } else {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return repository.updateUserAccount(request, request.getId());
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    public void deleteUserAccount(String id) {
        repository.deleteUserAccount(id, id);
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_PROFILE_DETAIL_KEY, @contextUtil.getUserId(), #id)"
    )
    public UserProfileResponse detailUserProfile(String id) {
        return repository.detailUserProfile(currentUserId(), currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_PROFILE_QUERY_KEY, @contextUtil.getUserId(), #request)"
    )
    public List<UserProfileResponse> queryUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        return repository.queryUserProfile(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE, allEntries = true)
    public UserProfileResponse createUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        request.setId(userId);
        return repository.createUserProfile(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE, allEntries = true)
    public UserProfileResponse updateUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        request.setId(userId);
        return repository.updateUserProfile(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE, allEntries = true)
    public void deleteUserProfile(String id) {
        repository.deleteUserProfile(currentUserId(), currentUserId());
    }

    private String currentUserId() {
        String userId = contextUtil.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
