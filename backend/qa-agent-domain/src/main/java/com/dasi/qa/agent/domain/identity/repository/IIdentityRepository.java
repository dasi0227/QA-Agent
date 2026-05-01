package com.dasi.qa.agent.domain.identity.repository;

import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.model.response.identity.UserProfileResponse;

import java.util.List;

public interface IIdentityRepository {

    UserAccountResponse detailUserAccount(String id, String userId);

    List<UserAccountResponse> queryUserAccount(UserAccountRequest request, String userId);

    UserAccountResponse createUserAccount(UserAccountRequest request, String userId);

    UserAccountResponse updateUserAccount(UserAccountRequest request, String userId);

    void deleteUserAccount(String id, String userId);

    UserAccountResponse findUserAccountByUsername(String username);

    UserAccountResponse findUserAccountByEmail(String email);

    UserProfileResponse detailUserProfile(String id, String userId);

    List<UserProfileResponse> queryUserProfile(UserProfileRequest request, String userId);

    UserProfileResponse createUserProfile(UserProfileRequest request, String userId);

    UserProfileResponse updateUserProfile(UserProfileRequest request, String userId);

    void deleteUserProfile(String id, String userId);
}
