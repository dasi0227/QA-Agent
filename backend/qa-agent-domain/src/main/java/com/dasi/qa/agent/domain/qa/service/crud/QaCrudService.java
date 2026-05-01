package com.dasi.qa.agent.domain.qa.service.crud;

import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.util.UserContextUtil;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.model.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.model.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.model.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QaCrudService implements IQaCrudService {

    private final IQaRepository repository;
    private final UserContextUtil userContext;

    public QaCrudService(IQaRepository repository, UserContextUtil userContext) {
        this.repository = repository;
        this.userContext = userContext;
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.QA_SET_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_SET_DETAIL_KEY, @userContext.getUserId(), #id)"
    )
    public QaSetResponse detailQaSet(String id) {
        return repository.detailQaSet(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.QA_SET_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_SET_QUERY_KEY, @userContext.getUserId(), #request)"
    )
    public List<QaSetResponse> queryQaSet(QaSetRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.queryQaSet(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_SET_CACHE, allEntries = true)
    public QaSetResponse createQaSet(QaSetRequest request) {
        String userId = currentUserId();
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setUserId(userId);
        return repository.createQaSet(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_SET_CACHE, allEntries = true)
    public QaSetResponse updateQaSet(QaSetRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.updateQaSet(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_SET_CACHE, allEntries = true)
    public void deleteQaSet(String id) {
        repository.deleteQaSet(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.QA_ITEM_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_ITEM_DETAIL_KEY, @userContext.getUserId(), #id)"
    )
    public QaItemResponse detailQaItem(String id) {
        return repository.detailQaItem(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.QA_ITEM_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_ITEM_QUERY_KEY, @userContext.getUserId(), #request)"
    )
    public List<QaItemResponse> queryQaItem(QaItemRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.queryQaItem(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public QaItemResponse createQaItem(QaItemRequest request) {
        String userId = currentUserId();
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setUserId(userId);
        return repository.createQaItem(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public QaItemResponse updateQaItem(QaItemRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.updateQaItem(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void deleteQaItem(String id) {
        repository.deleteQaItem(id, currentUserId());
    }

    private String currentUserId() {
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
