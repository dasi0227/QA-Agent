package com.dasi.qa.agent.domain.agent.repository;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.GeneratedQaSetSaveResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import com.dasi.qa.agent.domain.agent.service.assist.model.context.AssistContext;
import com.dasi.qa.agent.domain.agent.service.assist.model.result.AssistResult;
import com.dasi.qa.agent.domain.agent.service.complete.model.context.CompleteContext;
import com.dasi.qa.agent.domain.agent.service.complete.model.result.CompleteResult;
import com.dasi.qa.agent.domain.agent.service.memory.model.vo.SessionSource;
import com.dasi.qa.agent.domain.agent.service.memory.model.dto.Memory;
import com.dasi.qa.agent.domain.agent.service.memory.model.dto.MemoryEvidence;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.service.assess.model.command.AssessSaveCommand;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.SessionContext;
import com.dasi.qa.agent.domain.agent.service.feedback.model.command.FeedbackSaveCommand;
import com.dasi.qa.agent.domain.agent.model.vo.PracticeVO;

import com.dasi.qa.agent.domain.agent.model.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileStyleVO;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;

import java.util.List;
import java.time.LocalDateTime;

public interface IAgentRepository {

    void createGenerationTask(String taskId, String userId, CreateQaSetRequest request, UserProfileAllowVO allow);

    /**
     * 原子抢占任务：仅当 status = PENDING 时才更新为 PROCESSING。
     * @return true 表示抢占成功，false 表示任务已被其他线程抢占或已结束
     */
    boolean tryClaimTask(String taskId, String userId);

    void updateTaskStatus(String taskId, GenerateStatus status, GeneratePhase phase);

    default void updateTaskPhase(String taskId, GeneratePhase phase) {
        updateTaskStatus(taskId, GenerateStatus.PROCESSING, phase);
    }

    void markTaskCompleted(String taskId, String qaSetId);

    void markTaskFailed(String taskId, AgentErrorType agentErrorType, String errorMessage);

    void markTaskCanceled(String taskId);

    boolean isTaskCanceled(String taskId);

    void appendTaskMessage(String taskId, String userId, String stage, String message, String content);

    TaskStatusResponse getTaskStatus(String taskId, String userId);

    List<TaskMessageResponse> getTaskMessages(String taskId, String userId);

    List<TaskListItemResponse> getTaskList(String userId);


    UserProfileInfoVO getUserProfileInfo(String userId);

    UserProfileStyleVO getUserProfileStyle(String userId);

    UserProfileAllowVO getUserProfileAllow(String userId);

    String getDocumentsSummary(List<String> documentIds, String userId);

    GeneratedQaSetSaveResult saveGeneratedQaSet(String taskId, String userId, CreateQaSetRequest request, PlanResult planResult, List<DraftResult> draftResults);

    PracticeVO getPracticeVO(String sessionItemId, String userId);

    LocalDateTime saveFeedbackResult(String sessionItemId, String userId, FeedbackSaveCommand command);

    SessionContext getAssessContext(String sessionId, String userId);

    AssessResponse saveAssessResult(String sessionId, String userId, AssessSaveCommand command);

    AssistContext getAssistContext(String qaItemId, String userId);

    void saveAssistResult(String qaItemId, String userId, AssistResult result);

    CompleteContext getCompleteContext(String qaItemId, String userId);

    void saveCompleteResult(String qaItemId, String userId, CompleteResult result);

    void markQaItemCompleteFailed(String qaItemId, String userId);

    SessionSource getInvestContext(String sessionId, String userId);

    Memory findMemoryByKey(String userId, String memoryType, String targetType, String targetKey);

    void createMemory(Memory memory);

    void updateMemory(Memory memory);

    boolean existsMemoryEvidence(String memoryId, String sessionItemId);

    void createMemoryEvidence(MemoryEvidence evidence);

}
