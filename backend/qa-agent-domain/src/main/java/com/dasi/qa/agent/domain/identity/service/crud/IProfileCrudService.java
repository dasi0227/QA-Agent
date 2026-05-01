package com.dasi.qa.agent.domain.identity.service.crud;

import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.model.response.identity.UserProfileResponse;

import java.util.List;

public interface IProfileCrudService {

    UserAccountResponse detailUserAccount(String id);

    List<UserAccountResponse> queryUserAccount(UserAccountRequest request);

    UserAccountResponse createUserAccount(UserAccountRequest request);

    UserAccountResponse updateUserAccount(UserAccountRequest request);

    void deleteUserAccount(String id);

    UserProfileResponse detailUserProfile(String id);

    List<UserProfileResponse> queryUserProfile(UserProfileRequest request);

    UserProfileResponse createUserProfile(UserProfileRequest request);

    UserProfileResponse updateUserProfile(UserProfileRequest request);

    void deleteUserProfile(String id);
}
