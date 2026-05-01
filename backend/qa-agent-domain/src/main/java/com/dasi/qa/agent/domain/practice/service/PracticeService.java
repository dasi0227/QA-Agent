package com.dasi.qa.agent.domain.practice.service;

import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.domain.service.support.AbstractDomainServiceSupport;
import com.dasi.qa.agent.domain.util.UserContext;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PracticeService extends AbstractDomainServiceSupport implements IPracticeService {

    private static final String PRACTICE_SESSION_CACHE_NAME = "practice:practice-session";
    private static final String PRACTICE_SESSION_ITEM_CACHE_NAME = "practice:practice-session-item";

    private final IPracticeRepository repository;

    public PracticeService(IPracticeRepository repository, UserContext userContext) {
        super(userContext);
        this.repository = repository;
    }

    @Override
    @Cacheable(cacheNames = PRACTICE_SESSION_CACHE_NAME, key = "@cacheKeyBuilder.detail('practice:practice-session', @userContextImpl.getUserId(), #id)")
    public PracticeSessionResponse detailPracticeSession(String id) {
        return repository.detailPracticeSession(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = PRACTICE_SESSION_CACHE_NAME, key = "@cacheKeyBuilder.query('practice:practice-session', @userContextImpl.getUserId(), #request)")
    public List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request) {
        request.setUserId(currentUserId());
        return repository.queryPracticeSession(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = PRACTICE_SESSION_CACHE_NAME, allEntries = true)
    public PracticeSessionResponse createPracticeSession(PracticeSessionRequest request) {
        fillCommon(request, true);
        return repository.createPracticeSession(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = PRACTICE_SESSION_CACHE_NAME, allEntries = true)
    public PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request) {
        fillCommon(request, false);
        return repository.updatePracticeSession(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = PRACTICE_SESSION_CACHE_NAME, allEntries = true)
    public void deletePracticeSession(String id) {
        repository.deletePracticeSession(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = PRACTICE_SESSION_ITEM_CACHE_NAME, key = "@cacheKeyBuilder.detail('practice:practice-session-item', @userContextImpl.getUserId(), #id)")
    public PracticeSessionItemResponse detailPracticeSessionItem(String id) {
        return repository.detailPracticeSessionItem(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = PRACTICE_SESSION_ITEM_CACHE_NAME, key = "@cacheKeyBuilder.query('practice:practice-session-item', @userContextImpl.getUserId(), #request)")
    public List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request) {
        request.setUserId(currentUserId());
        return repository.queryPracticeSessionItem(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = PRACTICE_SESSION_ITEM_CACHE_NAME, allEntries = true)
    public PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request) {
        fillCommon(request, true);
        return repository.createPracticeSessionItem(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = PRACTICE_SESSION_ITEM_CACHE_NAME, allEntries = true)
    public PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request) {
        fillCommon(request, false);
        return repository.updatePracticeSessionItem(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = PRACTICE_SESSION_ITEM_CACHE_NAME, allEntries = true)
    public void deletePracticeSessionItem(String id) {
        repository.deletePracticeSessionItem(id, currentUserId());
    }
}
