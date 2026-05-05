package com.dasi.qa.agent.domain.qa.service.crud;

import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;

import java.util.List;

public interface IQaCrudService {

    QaSetResponse detailQaSet(String id);

    List<QaSetResponse> queryQaSet(QaSetRequest request);

    QaSetResponse updateQaSet(QaSetRequest request);

    void deleteQaSet(String id);

    QaItemResponse detailQaItem(String id);

    List<QaItemResponse> queryQaItem(QaItemRequest request);

    QaItemResponse createQaItem(QaItemRequest request);

    QaItemResponse updateQaItem(QaItemRequest request);

    void deleteQaItem(String id);
}
