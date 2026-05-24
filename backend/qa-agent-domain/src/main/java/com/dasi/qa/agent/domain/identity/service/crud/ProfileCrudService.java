package com.dasi.qa.agent.domain.identity.service.crud;

import com.dasi.qa.agent.domain.identity.model.enumeration.AccountStatus;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.domain.util.IOssUtil;
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

import java.util.List;

@Service
public class ProfileCrudService implements IProfileCrudService {

    private static final String AVATAR_ROOT_PATH = "avatar/";

    private final IIdentityRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final IContextUtil contextUtil;
    private final IIdUtil idUtil;
    private final IOssUtil ossUtil;

    public ProfileCrudService(IIdentityRepository repository, PasswordEncoder passwordEncoder, IContextUtil contextUtil,
                               IIdUtil idUtil, IOssUtil ossUtil) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.contextUtil = contextUtil;
        this.idUtil = idUtil;
        this.ossUtil = ossUtil;
    }

    @Override
    public UserAccountResponse detailUserAccount(String id) {
        return repository.detailUserAccount(id, contextUtil.getUserId());
    }

    @Override
    public List<UserAccountResponse> queryUserAccount(UserAccountRequest request) {
        return repository.queryUserAccount(request, contextUtil.getUserId());
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
        String userId = contextUtil.getUserId();
        request.setId(userId);
        request.setPassword(null);
        return repository.updateUserAccount(request, userId);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        String userId = contextUtil.getUserId();
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
    public UserAccountResponse updateAvatar(String originalFilename, String contentType, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ResultCode.BAD_REQUEST, "请选择要上传的头像图片");
        }
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new ApiException(ResultCode.FILE_INVALID, "请上传图片格式的头像");
        }

        String userId = contextUtil.getUserId();
        UserAccountResponse currentUser = repository.detailUserAccount(userId, userId);
        ossUtil.delete(currentUser.getAvatar());

        String extension = "png";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }

        String objectKey = AVATAR_ROOT_PATH + idUtil.nextId() + "." + extension;
        ossUtil.upload(bytes, objectKey);

        UserAccountRequest updateRequest = new UserAccountRequest();
        updateRequest.setId(userId);
        updateRequest.setAvatar(objectKey);
        repository.updateUserAccount(updateRequest, userId);

        UserAccountResponse updated = repository.detailUserAccount(userId, userId);
        updated.setAvatar(ossUtil.getPublicUrl(updated.getAvatar()));
        return updated;
    }

    @Override
    public void deleteUserAccount() {
        String userId = contextUtil.getUserId();
        repository.deleteUserAccount(userId, userId);
    }

    @Override
    public UserProfileResponse detailUserProfile() {
        String userId = contextUtil.getUserId();
        return repository.detailUserProfile(userId, userId);
    }

    @Override
    public List<UserProfileResponse> queryUserProfile(UserProfileRequest request) {
        return repository.queryUserProfile(request, contextUtil.getUserId());
    }

    @Override
    public UserProfileResponse createUserProfile(UserProfileRequest request) {
        String userId = contextUtil.getUserId();
        request.setId(userId);
        return repository.createUserProfile(request, userId);
    }

    @Override
    public UserProfileResponse updateUserProfile(UserProfileRequest request) {
        String userId = contextUtil.getUserId();
        request.setId(userId);
        return repository.updateUserProfile(request, userId);
    }

    @Override
    public void deleteUserProfile() {
        String userId = contextUtil.getUserId();
        repository.deleteUserProfile(userId, userId);
    }
}
