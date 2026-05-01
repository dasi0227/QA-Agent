package com.dasi.qa.agent.domain.document.service.crud;

import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.util.UserContextUtil;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.model.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.model.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.model.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentCrudCrudService implements IDocumentCrudService {

    private final IDocumentRepository repository;
    private final UserContextUtil userContext;

    public DocumentCrudCrudService(IDocumentRepository repository, UserContextUtil userContext) {
        this.repository = repository;
        this.userContext = userContext;
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_SOURCE_DOCUMENT_DETAIL_KEY, @userContext.getUserId(), #id)"
    )
    public SourceDocumentResponse detailSourceDocument(String id) {
        return repository.detailSourceDocument(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_SOURCE_DOCUMENT_QUERY_KEY, @userContext.getUserId(), #request)"
    )
    public List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.querySourceDocument(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, allEntries = true)
    public SourceDocumentResponse createSourceDocument(SourceDocumentRequest request) {
        String userId = currentUserId();
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setUserId(userId);
        return repository.createSourceDocument(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, allEntries = true)
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.updateSourceDocument(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, allEntries = true)
    public void deleteSourceDocument(String id) {
        repository.deleteSourceDocument(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE,
        key = "@redisKeyUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_CHUNK_DETAIL_KEY, @userContext.getUserId(), #id)"
    )
    public DocumentChunkResponse detailDocumentChunk(String id) {
        return repository.detailDocumentChunk(id, currentUserId());
    }

    @Override
    @Cacheable(
        cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE,
        key = "@redisKeyUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_CHUNK_QUERY_KEY, @userContext.getUserId(), #request)"
    )
    public List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.queryDocumentChunk(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request) {
        String userId = currentUserId();
        if (request.getId() == null || request.getId().isBlank()) {
            request.setId(UUID.randomUUID().toString());
        }
        request.setUserId(userId);
        return repository.createDocumentChunk(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request) {
        String userId = currentUserId();
        request.setUserId(userId);
        return repository.updateDocumentChunk(request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public void deleteDocumentChunk(String id) {
        repository.deleteDocumentChunk(id, currentUserId());
    }

    private String currentUserId() {
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ApiException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
