package com.dasi.qa.agent.domain.agent.repository;

import com.dasi.qa.agent.domain.agent.model.DraftItem;
import com.dasi.qa.agent.domain.agent.model.PlanResult;
import com.dasi.qa.agent.domain.agent.model.UserLlmConfig;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.enumeration.ErrorType;
import com.dasi.qa.agent.types.enumeration.GenerationStage;
import com.dasi.qa.agent.types.enumeration.GenerationStatus;

import java.util.List;

public interface IAgentRepository {

    void createGenerationTask(String taskId, String userId, CreateTaskRequest request);

    void updateTaskStage(String taskId, GenerationStatus status, GenerationStage stage);

    void markTaskCompleted(String taskId, String qaSetId);

    void markTaskFailed(String taskId, ErrorType errorType, String errorMessage);

    void appendTaskMessage(String taskId, GenerationStage stage, String message);

    TaskStatusResponse getTaskStatus(String taskId, String userId);

    List<TaskMessageResponse> getTaskMessages(String taskId, String userId);

    UserLlmConfig getUserLlmConfig(String userId);

    String getDocumentsSummary(List<String> documentIds, String userId);

    String saveGeneratedQaSet(String taskId, String userId, CreateTaskRequest request, PlanResult planResult, List<DraftItem> draftItems);

}
