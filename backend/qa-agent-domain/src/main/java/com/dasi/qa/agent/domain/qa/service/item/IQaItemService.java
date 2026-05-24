package com.dasi.qa.agent.domain.qa.service.item;

import com.dasi.qa.agent.types.dto.request.qa.QaItemCompleteRetryRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemBatchRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;

import java.util.List;

public interface IQaItemService {

    QaItemResponse createQaItem(CreateQaItemRequest request);

    List<QaItemResponse> createQaItems(CreateQaItemBatchRequest request);

    QaItemResponse completeQaItem(QaItemCompleteRetryRequest request);

    QaItemResponse detailQaItem(String id);

    List<QaItemResponse> queryQaItem(QaItemRequest request);

    QaItemResponse createQaItem(QaItemRequest request);

    QaItemResponse updateQaItem(QaItemRequest request);

    void deleteQaItem(String id);
}
