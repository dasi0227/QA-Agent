package com.dasi.qa.agent.interfaces.controller;

import cn.hutool.core.util.StrUtil;
import com.dasi.qa.agent.domain.identity.service.crud.IProfileCrudService;
import com.dasi.qa.agent.domain.util.IOssUtil;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.Result;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

import static com.dasi.qa.agent.types.constant.StringConstant.AVATAR_ROOT_PATH;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final IProfileCrudService identityService;
    private final IOssUtil aliOssUtil;
    private final IContextUtil contextUtil;

    public IdentityController(IProfileCrudService identityService, IOssUtil aliOssUtil, IContextUtil contextUtil) {
        this.identityService = identityService;
        this.aliOssUtil = aliOssUtil;
        this.contextUtil = contextUtil;
    }

    @PostMapping("/account/update")
    public Result<UserAccountResponse> userAccountUpdate(@RequestBody UserAccountRequest request) {
        return Result.success(identityService.updateUserAccount(request));
    }

    @PostMapping("/account/delete")
    public Result<Void> userAccountDelete(@RequestBody UserAccountRequest request) {
        identityService.deleteUserAccount(request.getId());
        return Result.success();
    }

    @GetMapping("/profile/me")
    public Result<UserProfileResponse> userProfileMe() {
        return Result.success(identityService.detailUserProfile("self"));
    }

    @PostMapping("/profile/create")
    public Result<UserProfileResponse> userProfileCreate(@RequestBody UserProfileRequest request) {
        return Result.success(identityService.createUserProfile(request));
    }

    @PostMapping("/profile/update")
    public Result<UserProfileResponse> userProfileUpdate(@RequestBody UserProfileRequest request) {
        return Result.success(identityService.updateUserProfile(request));
    }

    @PostMapping("/account/avatar")
    public Result<UserAccountResponse> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        String userId = contextUtil.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        UserAccountResponse currentUser = identityService.detailUserAccount(userId);

        aliOssUtil.delete(currentUser.getAvatar());

        String originalFilename = file.getOriginalFilename();
        String extension = "png";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }

        byte[] bytes = file.getBytes();
        String objectKey = AVATAR_ROOT_PATH + UUID.randomUUID() + "." + extension;
        aliOssUtil.upload(bytes, objectKey);

        UserAccountRequest updateRequest = new UserAccountRequest();
        updateRequest.setId(userId);
        updateRequest.setAvatar(objectKey);
        identityService.updateUserAccount(updateRequest);

        UserAccountResponse updated = identityService.detailUserAccount(userId);
        updated.setAvatar(aliOssUtil.getPublicUrl(updated.getAvatar()));
        return Result.success(updated);
    }
}
