package com.dasi.qa.agent.domain.qa.service.crud;

import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class QaCrudService implements IQaCrudService {

    private final IQaRepository repository;
    private final IContextUtil contextUtil;

    public QaCrudService(IQaRepository repository, IContextUtil contextUtil) {
        this.repository = repository;
        this.contextUtil = contextUtil;
    }

    @Override
    public QaSetResponse detailQaSet(String id) {
        return repository.detailQaSet(id, currentUserId());
    }

    @Override
    public List<QaSetResponse> queryQaSet(QaSetRequest request) {
        return repository.queryQaSet(request, currentUserId());
    }

    @Override
    public QaSetResponse updateQaSet(QaSetRequest request) {
        return repository.updateQaSet(request, currentUserId());
    }

    @Override
    public void deleteQaSet(String id) {
        repository.deleteQaSet(id, currentUserId());
    }

    @Override
    public QaItemResponse detailQaItem(String id) {
        return repository.detailQaItem(id, currentUserId());
    }

    @Override
    public List<QaItemResponse> queryQaItem(QaItemRequest request) {
        return repository.queryQaItem(request, currentUserId());
    }

    @Override
    public QaItemResponse createQaItem(QaItemRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            request.setId(UUID.randomUUID().toString());
        }
        return repository.createQaItem(request, currentUserId());
    }

    @Override
    public QaItemResponse updateQaItem(QaItemRequest request) {
        return repository.updateQaItem(request, currentUserId());
    }

    @Override
    public void deleteQaItem(String id) {
        repository.deleteQaItem(id, currentUserId());
    }

    private String currentUserId() {
        return contextUtil.getUserId();
    }
}
