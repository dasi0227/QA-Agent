package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.identity.service.crud.IProfileCrudService;
import com.dasi.qa.agent.types.dto.request.identity.ChangePasswordRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/identity")
public class IdentityController {

    private final IProfileCrudService identityService;

    public IdentityController(IProfileCrudService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/account/update")
    public Result<UserAccountResponse> userAccountUpdate(@RequestBody UserAccountRequest request) {
        return Result.success(identityService.updateUserAccount(request));
    }

    @PostMapping("/account/password")
    public Result<Void> accountPasswordUpdate(@RequestBody @Valid ChangePasswordRequest request) {
        identityService.changePassword(request);
        return Result.success();
    }

    @PostMapping("/account/delete")
    public Result<Void> userAccountDelete() {
        identityService.deleteUserAccount();
        return Result.success();
    }

    @GetMapping("/profile/me")
    public Result<UserProfileResponse> userProfileMe() {
        return Result.success(identityService.detailUserProfile());
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
        return Result.success(identityService.updateAvatar(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
    }
}
