package com.dasi.qa.agent.domain.service;

import com.dasi.qa.agent.types.model.request.BaseRequest;
import com.dasi.qa.agent.types.model.response.BaseResponse;

import java.util.List;

public interface CrudService<C extends BaseRequest, R extends BaseResponse> {

    R detail(String id);

    List<R> query(C command);

    R create(C command);

    R update(C command);

    void delete(String id);
}
