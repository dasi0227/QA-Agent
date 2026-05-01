package com.dasi.qa.agent.domain.qa.service;

import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.service.support.AbstractDomainServiceSupport;
import com.dasi.qa.agent.domain.util.UserContext;
import com.dasi.qa.agent.types.model.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.model.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.model.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.model.response.qa.QaSetResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QaService extends AbstractDomainServiceSupport implements IQaService {

    private static final String QA_SET_CACHE_NAME = "qa:qa-set";
    private static final String QA_ITEM_CACHE_NAME = "qa:qa-item";

    private final IQaRepository repository;

    public QaService(IQaRepository repository, UserContext userContext) {
        super(userContext);
        this.repository = repository;
    }

    @Override
    @Cacheable(cacheNames = QA_SET_CACHE_NAME, key = "@cacheKeyBuilder.detail('qa:qa-set', @userContextImpl.getUserId(), #id)")
    public QaSetResponse detailQaSet(String id) {
        return repository.detailQaSet(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = QA_SET_CACHE_NAME, key = "@cacheKeyBuilder.query('qa:qa-set', @userContextImpl.getUserId(), #request)")
    public List<QaSetResponse> queryQaSet(QaSetRequest request) {
        request.setUserId(currentUserId());
        return repository.queryQaSet(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = QA_SET_CACHE_NAME, allEntries = true)
    public QaSetResponse createQaSet(QaSetRequest request) {
        fillCommon(request, true);
        return repository.createQaSet(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = QA_SET_CACHE_NAME, allEntries = true)
    public QaSetResponse updateQaSet(QaSetRequest request) {
        fillCommon(request, false);
        return repository.updateQaSet(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = QA_SET_CACHE_NAME, allEntries = true)
    public void deleteQaSet(String id) {
        repository.deleteQaSet(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = QA_ITEM_CACHE_NAME, key = "@cacheKeyBuilder.detail('qa:qa-item', @userContextImpl.getUserId(), #id)")
    public QaItemResponse detailQaItem(String id) {
        return repository.detailQaItem(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = QA_ITEM_CACHE_NAME, key = "@cacheKeyBuilder.query('qa:qa-item', @userContextImpl.getUserId(), #request)")
    public List<QaItemResponse> queryQaItem(QaItemRequest request) {
        request.setUserId(currentUserId());
        return repository.queryQaItem(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = QA_ITEM_CACHE_NAME, allEntries = true)
    public QaItemResponse createQaItem(QaItemRequest request) {
        fillCommon(request, true);
        return repository.createQaItem(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = QA_ITEM_CACHE_NAME, allEntries = true)
    public QaItemResponse updateQaItem(QaItemRequest request) {
        fillCommon(request, false);
        return repository.updateQaItem(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = QA_ITEM_CACHE_NAME, allEntries = true)
    public void deleteQaItem(String id) {
        repository.deleteQaItem(id, currentUserId());
    }
}
