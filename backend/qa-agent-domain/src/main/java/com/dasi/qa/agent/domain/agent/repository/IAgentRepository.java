package com.dasi.qa.agent.domain.agent.repository;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.shared.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileStyleVO;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;

import java.util.List;

public interface IAgentRepository {

    void createGenerationTask(String taskId, String userId, CreateQaSetRequest request, UserProfileAllowVO allow);

    void updateTaskStatus(String taskId, GenerateStatus status, GeneratePhase phase);

    default void updateTaskPhase(String taskId, GeneratePhase phase) {
        updateTaskStatus(taskId, GenerateStatus.PROCESSING, phase);
    }

    void markTaskCompleted(String taskId, String qaSetId);

    void markTaskFailed(String taskId, ErrorType errorType, String errorMessage);

    void appendTaskMessage(String taskId, String userId, GeneratePhase phase, String message, String content);

    TaskStatusResponse getTaskStatus(String taskId, String userId);

    List<TaskMessageResponse> getTaskMessages(String taskId, String userId);

    List<TaskListItemResponse> getTaskList(String userId);

    UserLlmModelVO getUserLlmModel(String userId);

    UserProfileInfoVO getUserProfileInfo(String userId);

    UserProfileStyleVO getUserProfileStyle(String userId);

    UserProfileAllowVO getUserProfileAllow(String userId);

    String getDocumentsSummary(List<String> documentIds, String userId);

    String saveGeneratedQaSet(String taskId, String userId, CreateQaSetRequest request, PlanResult planResult, List<DraftResult> draftResults);

}
