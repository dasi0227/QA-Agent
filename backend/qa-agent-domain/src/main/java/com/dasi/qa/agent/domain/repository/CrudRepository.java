package com.dasi.qa.agent.domain.repository;

import com.dasi.qa.agent.types.model.request.BaseRequest;
import com.dasi.qa.agent.types.model.response.BaseResponse;

import java.util.List;

public interface CrudRepository<C extends BaseRequest, R extends BaseResponse> {

    R detail(String id, String userId);

    List<R> query(C command, String userId);

    R create(C command, String userId);

    R update(C command, String userId);

    void delete(String id, String userId);
}
