package com.dasi.qa.agent.domain.qa.service.item;

import com.dasi.qa.agent.domain.agent.service.complete.ICompleteAgent;
import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.types.dto.request.qa.QaItemCompleteRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemDraft;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemBatchRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemSingleRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class QaItemService implements IQaItemService {

    private final int MAX_CREATE_ITEM_NUM = 20;

    private final IQaRepository repository;
    private final IContextUtil contextUtil;
    private final IIdUtil idUtil;
    private final ICompleteAgent completeAgent;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public QaItemService(IQaRepository repository,
                         IContextUtil contextUtil,
                         IIdUtil idUtil,
                         ICompleteAgent completeAgent,
                         @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.repository = repository;
        this.contextUtil = contextUtil;
        this.idUtil = idUtil;
        this.completeAgent = completeAgent;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public QaItemResponse detailQaItem(String id) {
        QaItemResponse response = repository.detailQaItem(id, contextUtil.getUserId());
        repository.fillQaItemPracticeStats(response, contextUtil.getUserId());
        return response;
    }

    @Override
    public List<QaItemResponse> queryQaItem(QaItemRequest request) {
        return repository.queryQaItem(request, contextUtil.getUserId());
    }

    @Override
    public QaItemResponse createQaItem(QaItemRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            request.setId(idUtil.nextId());
        }
        return repository.createQaItem(request, contextUtil.getUserId());
    }

    @Override
    public QaItemResponse updateQaItem(QaItemRequest request) {
        return repository.updateQaItem(request, contextUtil.getUserId());
    }

    @Override
    public void deleteQaItem(String id) {
        repository.deleteQaItem(id, contextUtil.getUserId());
    }

    @Override
    public QaItemResponse createQaItem(CreateQaItemSingleRequest request) {
        String userId = contextUtil.getUserId();
        if (repository.existsQaItemByQuestion(request.getQaSetId(), request.getQuestion(), userId)) {
            throw new ApiException(ResultCode.CONFLICT, "该题目已存在于此题集中");
        }
        QaItemResponse response = repository.createQaItem(idUtil.nextId(), request, userId);
        applicationTaskExecutor.execute(() -> completeAgent.execute(response.getId(), userId));
        return response;
    }

    @Override
    public List<QaItemResponse> createQaItems(CreateQaItemBatchRequest request) {
        List<QaItemDraft> items = request.getItems().stream()
                .filter(item -> item != null && StringUtils.hasText(item.getQuestion()))
                .map(item -> new QaItemDraft(item.getQuestion().trim(), item.getAnswer()))
                .toList();
        if (items.isEmpty()) {
            throw new ApiException(ResultCode.BAD_REQUEST, "请至少输入 1 道题目");
        }
        if (items.size() > MAX_CREATE_ITEM_NUM) {
            throw new ApiException(ResultCode.BAD_REQUEST, "单次最多新增 20 道题目");
        }
        CreateQaItemBatchRequest normalizedRequest = CreateQaItemBatchRequest.builder()
                .qaSetId(request.getQaSetId())
                .items(items)
                .build();
        String userId = contextUtil.getUserId();
        for (QaItemDraft item : items) {
            if (repository.existsQaItemByQuestion(normalizedRequest.getQaSetId(), item.getQuestion(), userId)) {
                throw new ApiException(ResultCode.CONFLICT, "题目「" + item.getQuestion() + "」已存在于此题集中");
            }
        }
        List<String> ids = items.stream().map(item -> idUtil.nextId()).toList();
        List<QaItemResponse> responses = repository.createQaItems(ids, normalizedRequest, userId);
        for (QaItemResponse response : responses) {
            applicationTaskExecutor.execute(() -> completeAgent.execute(response.getId(), userId));
        }
        return responses;
    }

    @Override
    public QaItemResponse completeQaItem(QaItemCompleteRequest request) {
        String userId = contextUtil.getUserId();
        QaItemResponse response = repository.markQaItemCompleteProcessing(request, userId);
        applicationTaskExecutor.execute(() -> completeAgent.execute(response.getId(), userId));
        return response;
    }

}
