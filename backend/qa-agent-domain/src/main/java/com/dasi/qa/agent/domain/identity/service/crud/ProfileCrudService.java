package com.dasi.qa.agent.domain.identity.service.crud;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.model.enumeration.AccountStatus;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.ResultCode;
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
    public UserAccountResponse detailUserAccount(String id) {
        return repository.detailUserAccount(id, id);
    }

    @Override
    public List<UserAccountResponse> queryUserAccount(UserAccountRequest request) {
        return repository.queryUserAccount(request, request.getId());
    }

    @Override
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
    public void deleteUserAccount(String id) {
        repository.deleteUserAccount(id, id);
    }

    @Override
    public UserProfileResponse detailUserProfile(String id) {
        return repository.detailUserProfile(currentUserId(), currentUserId());
    }

    @Override
    public List<UserProfileResponse> queryUserProfile(UserProfileRequest request) {
        return repository.queryUserProfile(request, currentUserId());
    }

    @Override
    public UserProfileResponse createUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        request.setId(userId);
        return repository.createUserProfile(request, userId);
    }

    @Override
    public UserProfileResponse updateUserProfile(UserProfileRequest request) {
        String userId = currentUserId();
        request.setId(userId);
        return repository.updateUserProfile(request, userId);
    }

    @Override
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
