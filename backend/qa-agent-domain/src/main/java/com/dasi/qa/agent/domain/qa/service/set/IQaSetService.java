package com.dasi.qa.agent.domain.qa.service.set;

import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetImportRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaSetExportResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;

import java.util.List;

public interface IQaSetService {

    QaSetResponse detailQaSet(String id);

    List<QaSetResponse> queryQaSet(QaSetRequest request);

    QaSetResponse updateQaSet(QaSetRequest request);

    void deleteQaSet(String id);

    QaSetExportResponse exportQaSet(String id);

    QaSetResponse importQaSet(QaSetImportRequest request);
}
