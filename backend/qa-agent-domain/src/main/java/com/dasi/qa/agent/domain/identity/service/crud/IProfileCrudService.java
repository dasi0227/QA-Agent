package com.dasi.qa.agent.domain.identity.service.crud;

import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.ChangePasswordRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;

import java.util.List;

public interface IProfileCrudService {

    UserAccountResponse detailUserAccount(String id);

    List<UserAccountResponse> queryUserAccount(UserAccountRequest request);

    UserAccountResponse createUserAccount(UserAccountRequest request);

    UserAccountResponse updateUserAccount(UserAccountRequest request);

    void changePassword(ChangePasswordRequest request);

    UserAccountResponse updateAvatar(String originalFilename, String contentType, byte[] bytes);

    void deleteUserAccount();

    UserProfileResponse detailUserProfile();

    List<UserProfileResponse> queryUserProfile(UserProfileRequest request);

    UserProfileResponse createUserProfile(UserProfileRequest request);

    UserProfileResponse updateUserProfile(UserProfileRequest request);

    void deleteUserProfile();
}
