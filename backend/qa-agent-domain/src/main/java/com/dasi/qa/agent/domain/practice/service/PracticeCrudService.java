package com.dasi.qa.agent.domain.practice.service;

import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.domain.practice.service.crud.IPracticeCrudService;
import com.dasi.qa.agent.domain.util.UserContextUtil;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PracticeCrudService implements IPracticeCrudService {

    private final IPracticeRepository repository;
    private final UserContextUtil userContext;

    public PracticeCrudService(IPracticeRepository repository, UserContextUtil userContext) {
        this.repository = repository;
        this.userContext = userContext;
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.PRACTICE_SESSION_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_DETAIL_KEY, @userContext.getUserId(), #id)"
    )
    public PracticeSessionResponse detailPracticeSession(String id) {
        return repository.detailPracticeSession(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.PRACTICE_SESSION_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_QUERY_KEY, @userContext.getUserId(), #request)"
    )
    public List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.queryPracticeSession(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeSessionResponse createPracticeSession(PracticeSessionRequest request) {
        String userId = currentUserId();
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setUserId(userId);
        return repository.createPracticeSession(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.updatePracticeSession(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public void deletePracticeSession(String id) {
        repository.deletePracticeSession(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_ITEM_DETAIL_KEY, @userContext.getUserId(), #id)"
    )
    public PracticeSessionItemResponse detailPracticeSessionItem(String id) {
        return repository.detailPracticeSessionItem(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_ITEM_QUERY_KEY, @userContext.getUserId(), #request)"
    )
    public List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.queryPracticeSessionItem(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, allEntries = true)
    public PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request) {
        String userId = currentUserId();
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setUserId(userId);
        return repository.createPracticeSessionItem(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, allEntries = true)
    public PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.updatePracticeSessionItem(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, allEntries = true)
    public void deletePracticeSessionItem(String id) {
        repository.deletePracticeSessionItem(id, currentUserId());
    }

    private String currentUserId() {
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
