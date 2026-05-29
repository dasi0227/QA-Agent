package com.dasi.qa.agent.domain.qa.service.set;

import com.dasi.qa.agent.domain.agent.service.shared.SseEvent;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetReindexRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetImportRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateEmptyQaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaSetExportResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskCreateResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;

import java.util.List;
import java.util.function.Consumer;

public interface IQaSetService {

    QaSetResponse detailQaSet(String id);

    List<QaSetResponse> queryQaSet(QaSetRequest request);

    QaSetResponse createEmptyQaSet(CreateEmptyQaSetRequest request);

    QaSetResponse updateQaSet(QaSetRequest request);

    void deleteQaSet(String id);

    QaSetExportResponse exportQaSet(String id);

    QaSetResponse importQaSet(QaSetImportRequest request);

    TaskCreateResponse createTask(CreateQaSetRequest request);

    void abortTask(String taskId, String userId);

    void createQaSet(CreateQaSetRequest request, Consumer<SseEvent> sseEventHandler);

    TaskStatusResponse getTaskStatus(String taskId);

    List<TaskMessageResponse> getTaskMessages(String taskId);

    void reindexQaSet(QaSetReindexRequest request);

    List<TaskListItemResponse> getTaskList();
}
