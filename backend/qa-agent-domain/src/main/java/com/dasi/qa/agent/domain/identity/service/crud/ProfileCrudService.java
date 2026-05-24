package com.dasi.qa.agent.domain.identity.service.crud;

import com.dasi.qa.agent.domain.identity.model.enumeration.AccountStatus;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.identity.ChangePasswordRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dasi.qa.agent.domain.util.IIdUtil;
import java.util.List;

@Service
public class ProfileCrudService implements IProfileCrudService {

    private final IIdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IContextUtil contextUtil;
    private final IIdUtil idUtil;

    public ProfileCrudService(IIdentityRepository repository, PasswordEncoder passwordEncoder, IContextUtil contextUtil,
                               IIdUtil idUtil) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.contextUtil = contextUtil;
        this.idUtil = idUtil;
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
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new ApiException(ResultCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        if (!StringUtils.hasText(request.getId())) {
            request.setId(idUtil.nextId());
        }
        request.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : AccountStatus.ACTIVE.name());
        if (StringUtils.hasText(request.getPassword())) {
            request.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        return repository.createUserAccount(request, request.getId());
    }

    @Override
    public UserAccountResponse updateUserAccount(UserAccountRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            throw new ApiException(ResultCode.BAD_REQUEST, "账号 ID 不能为空");
        }
        request.setPassword(null);
        return repository.updateUserAccount(request, request.getId());
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        String userId = currentUserId();
        UserAccountResponse current = repository.detailUserAccount(userId, userId);
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new ApiException(ResultCode.BAD_REQUEST, "新密码不能和当前密码相同");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), current.getPassword())) {
            throw new ApiException(ResultCode.PASSWORD_INVALID);
        }
        repository.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));
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
        return contextUtil.getUserId();
    }
}
