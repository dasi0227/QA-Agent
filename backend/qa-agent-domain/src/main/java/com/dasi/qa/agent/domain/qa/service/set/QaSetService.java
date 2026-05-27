package com.dasi.qa.agent.domain.qa.service.set;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.IGenerateAgent;
import com.dasi.qa.agent.domain.agent.service.shared.SseEvent;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.qa.service.convert.QaSetConverter;
import com.dasi.qa.agent.domain.qa.service.convert.QaSetExportFile;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateEmptyQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetImportRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetExportResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskCreateResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.exception.ConvertException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class QaSetService implements IQaSetService {

    private final IQaRepository repository;
    private final IContextUtil contextUtil;
    private final QaSetConverter converter;
    private final IIdUtil idUtil;
    private final IAgentRepository agentRepository;
    private final IDocumentRepository documentRepository;
    private final IGenerateAgent generateAgent;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public QaSetService(IQaRepository repository,
                        IContextUtil contextUtil,
                        QaSetConverter converter,
                        IIdUtil idUtil,
                        IAgentRepository agentRepository,
                        IDocumentRepository documentRepository,
                        IGenerateAgent generateAgent,
                        @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.repository = repository;
        this.contextUtil = contextUtil;
        this.converter = converter;
        this.idUtil = idUtil;
        this.agentRepository = agentRepository;
        this.documentRepository = documentRepository;
        this.generateAgent = generateAgent;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public QaSetResponse detailQaSet(String id) {
        return repository.detailQaSet(id, contextUtil.getUserId());
    }

    @Override
    public List<QaSetResponse> queryQaSet(QaSetRequest request) {
        return repository.queryQaSet(request, contextUtil.getUserId());
    }

    @Override
    public QaSetResponse createEmptyQaSet(CreateEmptyQaSetRequest request) {
        return repository.createEmptyQaSet(idUtil.nextId(), request, contextUtil.getUserId());
    }

    @Override
    public QaSetResponse updateQaSet(QaSetRequest request) {
        return repository.updateQaSet(request, contextUtil.getUserId());
    }

    @Override
    public void deleteQaSet(String id) {
        repository.deleteQaSet(id, contextUtil.getUserId());
    }

    @Override
    public QaSetExportResponse exportQaSet(String id) {
        String userId = contextUtil.getUserId();
        QaSetResponse qaSet = repository.detailQaSet(id, userId);
        List<QaItemResponse> items = repository.queryQaItemsBySetId(id, userId);
        return QaSetExportResponse.builder()
                .fileName(converter.buildFileName(qaSet.getTitle()))
                .content(converter.exportContent(qaSet, items))
                .build();
    }

    @Override
    public QaSetResponse importQaSet(QaSetImportRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getFileName())
                || !request.getFileName().toLowerCase().endsWith(".dasi")
                || request.getContent() == null
                || request.getContent().length == 0) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID, "请上传有效的 .dasi 问答集文件");
        }
        QaSetExportFile exportFile = converter.importContent(request.getContent());
        return repository.importQaSet(exportFile, contextUtil.getUserId());
    }

    @Override
    public TaskCreateResponse createTask(CreateQaSetRequest request) {
        String userId = contextUtil.getUserId();
        validateDocumentFinished(request.getDocumentIds(), userId);
        String taskId = idUtil.nextId();
        agentRepository.createGenerationTask(taskId, userId, request, agentRepository.getUserProfileAllow(userId));
        return TaskCreateResponse.builder().taskId(taskId).build();
    }

    @Override
    public void createQaSet(CreateQaSetRequest request, Consumer<SseEvent> sseEventHandler) {
        if (!StringUtils.hasText(request.getTaskId())) {
            throw new ApiException(ResultCode.BAD_REQUEST, "生成任务 ID 不能为空，请先创建生成任务");
        }
        String userId = contextUtil.getUserId();
        validateDocumentFinished(request.getDocumentIds(), userId);
        applicationTaskExecutor.execute(() -> generateAgent.execute(userId, request, sseEventHandler));
    }

    private void validateDocumentFinished(List<String> documentIds, String userId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return;
        }
        Set<String> finishedIds = documentRepository.listFinishedDocuments(userId).stream()
                .map(SourceDocumentResponse::getId)
                .collect(Collectors.toSet());
        for (String docId : documentIds) {
            if (!finishedIds.contains(docId)) {
                throw new ApiException(ResultCode.BAD_REQUEST, "资料尚未完成索引，请稍后重试");
            }
        }
    }

    @Override
    public TaskStatusResponse getTaskStatus(String taskId) {
        return agentRepository.getTaskStatus(taskId, contextUtil.getUserId());
    }

    @Override
    public List<TaskMessageResponse> getTaskMessages(String taskId) {
        return agentRepository.getTaskMessages(taskId, contextUtil.getUserId());
    }

    @Override
    public List<TaskListItemResponse> getTaskList() {
        return agentRepository.getTaskList(contextUtil.getUserId());
    }

}
