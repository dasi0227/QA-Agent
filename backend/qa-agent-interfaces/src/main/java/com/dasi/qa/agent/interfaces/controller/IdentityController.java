package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.identity.service.IIdentityService;
import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.model.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IdentityController {

    private final IIdentityService identityService;

    public IdentityController(IIdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/user-account/detail")
    public Result<UserAccountResponse> userAccountDetail(@RequestParam("id") String id) {
        return Result.success(identityService.detailUserAccount(id));
    }

    @PostMapping("/user-account/query")
    public Result<List<UserAccountResponse>> userAccountQuery(@RequestBody UserAccountRequest request) {
        return Result.success(identityService.queryUserAccount(request));
    }

    @PostMapping("/user-account/create")
    public Result<UserAccountResponse> userAccountCreate(@RequestBody UserAccountRequest request) {
        return Result.success(identityService.createUserAccount(request));
    }

    @PostMapping("/user-account/update")
    public Result<UserAccountResponse> userAccountUpdate(@RequestBody UserAccountRequest request) {
        return Result.success(identityService.updateUserAccount(request));
    }

    @PostMapping("/user-account/delete")
    public Result<Void> userAccountDelete(@RequestBody UserAccountRequest request) {
        identityService.deleteUserAccount(request.getId());
        return Result.success();
    }

    @GetMapping("/user-profile/detail")
    public Result<UserProfileResponse> userProfileDetail(@RequestParam("id") String id) {
        return Result.success(identityService.detailUserProfile(id));
    }

    @PostMapping("/user-profile/query")
    public Result<List<UserProfileResponse>> userProfileQuery(@RequestBody UserProfileRequest request) {
        return Result.success(identityService.queryUserProfile(request));
    }

    @PostMapping("/user-profile/create")
    public Result<UserProfileResponse> userProfileCreate(@RequestBody UserProfileRequest request) {
        return Result.success(identityService.createUserProfile(request));
    }

    @PostMapping("/user-profile/update")
    public Result<UserProfileResponse> userProfileUpdate(@RequestBody UserProfileRequest request) {
        return Result.success(identityService.updateUserProfile(request));
    }

    @PostMapping("/user-profile/delete")
    public Result<Void> userProfileDelete(@RequestBody UserProfileRequest request) {
        identityService.deleteUserProfile(request.getId());
        return Result.success();
    }
}
