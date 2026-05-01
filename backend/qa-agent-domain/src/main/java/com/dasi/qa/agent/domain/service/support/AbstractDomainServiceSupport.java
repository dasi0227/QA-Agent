package com.dasi.qa.agent.domain.service.support;

import com.dasi.qa.agent.domain.util.UserContext;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.BaseRequest;
import com.dasi.qa.agent.types.result.ResultCode;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class AbstractDomainServiceSupport {

    private final UserContext userContext;

    protected AbstractDomainServiceSupport(UserContext userContext) {
        this.userContext = userContext;
    }

    protected String currentUserId() {
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    protected void fillCommon(BaseRequest request, boolean isCreate) {
        String currentUserId = currentUserId();
        if (isCreate) {
            if (request.getId() == null || request.getId().isBlank()) {
                request.setId(UUID.randomUUID().toString());
            }
            request.setCreatedAt(LocalDateTime.now());
        }
        request.setUserId(currentUserId);
        request.setUpdatedAt(LocalDateTime.now());
    }
}
