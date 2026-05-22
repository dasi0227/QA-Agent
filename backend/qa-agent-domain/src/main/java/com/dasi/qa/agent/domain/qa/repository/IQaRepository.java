package com.dasi.qa.agent.domain.qa.repository;

import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.domain.qa.service.convert.QaSetExportFile;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;

import java.util.List;

public interface IQaRepository {

    QaSetResponse detailQaSet(String id, String userId);

    List<QaSetResponse> queryQaSet(QaSetRequest request, String userId);

    QaSetResponse updateQaSet(QaSetRequest request, String userId);

    void deleteQaSet(String id, String userId);

    List<QaItemResponse> queryQaItemsBySetId(String qaSetId, String userId);

    QaSetResponse importQaSet(QaSetExportFile portableFile, String userId);

    QaItemResponse detailQaItem(String id, String userId);

    List<QaItemResponse> queryQaItem(QaItemRequest request, String userId);

    QaItemResponse createQaItem(QaItemRequest request, String userId);

    QaItemResponse createQaItem(String id, CreateQaItemRequest request, String userId);

    QaItemResponse updateQaItem(QaItemRequest request, String userId);

    QaItemResponse markQaItemCompleteProcessing(String id, String userId);

    void deleteQaItem(String id, String userId);
}
