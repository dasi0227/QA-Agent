package com.dasi.qa.agent.domain.agent.repository;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.shared.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileVO;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;

import java.util.List;

public interface IAgentRepository {

    void createGenerationTask(String taskId, String userId, CreateQaSetRequest request);

    void updateTaskStatus(String taskId, GenerateStatus status, GeneratePhase phase);

    void markTaskCompleted(String taskId, String qaSetId);

    void markTaskFailed(String taskId, ErrorType errorType, String errorMessage);

    void appendTaskMessage(String taskId, GeneratePhase phase, String message);

    TaskStatusResponse getTaskStatus(String taskId, String userId);

    List<TaskMessageResponse> getTaskMessages(String taskId, String userId);

    UserLlmModelVO getUserLlmModel(String userId);

    UserProfileVO getUserProfile(String userId);

    String getDocumentsSummary(List<String> documentIds, String userId);

    String saveGeneratedQaSet(String taskId, String userId, CreateQaSetRequest request, PlanResult planResult, List<DraftItem> draftItems);

}
