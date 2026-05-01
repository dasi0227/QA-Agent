package com.dasi.qa.agent.domain.document.service;

import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.service.support.AbstractDomainServiceSupport;
import com.dasi.qa.agent.domain.util.UserContext;
import com.dasi.qa.agent.types.model.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.model.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.model.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.model.response.document.SourceDocumentResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService extends AbstractDomainServiceSupport implements IDocumentService {

    private static final String SOURCE_DOCUMENT_CACHE_NAME = "document:source-document";
    private static final String DOCUMENT_CHUNK_CACHE_NAME = "document:document-chunk";

    private final IDocumentRepository repository;

    public DocumentService(IDocumentRepository repository, UserContext userContext) {
        super(userContext);
        this.repository = repository;
    }

    @Override
    @Cacheable(cacheNames = SOURCE_DOCUMENT_CACHE_NAME, key = "@cacheKeyBuilder.detail('document:source-document', @userContextImpl.getUserId(), #id)")
    public SourceDocumentResponse detailSourceDocument(String id) {
        return repository.detailSourceDocument(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = SOURCE_DOCUMENT_CACHE_NAME, key = "@cacheKeyBuilder.query('document:source-document', @userContextImpl.getUserId(), #request)")
    public List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request) {
        request.setUserId(currentUserId());
        return repository.querySourceDocument(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = SOURCE_DOCUMENT_CACHE_NAME, allEntries = true)
    public SourceDocumentResponse createSourceDocument(SourceDocumentRequest request) {
        fillCommon(request, true);
        return repository.createSourceDocument(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = SOURCE_DOCUMENT_CACHE_NAME, allEntries = true)
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request) {
        fillCommon(request, false);
        return repository.updateSourceDocument(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = SOURCE_DOCUMENT_CACHE_NAME, allEntries = true)
    public void deleteSourceDocument(String id) {
        repository.deleteSourceDocument(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = DOCUMENT_CHUNK_CACHE_NAME, key = "@cacheKeyBuilder.detail('document:document-chunk', @userContextImpl.getUserId(), #id)")
    public DocumentChunkResponse detailDocumentChunk(String id) {
        return repository.detailDocumentChunk(id, currentUserId());
    }

    @Override
    @Cacheable(cacheNames = DOCUMENT_CHUNK_CACHE_NAME, key = "@cacheKeyBuilder.query('document:document-chunk', @userContextImpl.getUserId(), #request)")
    public List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request) {
        request.setUserId(currentUserId());
        return repository.queryDocumentChunk(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = DOCUMENT_CHUNK_CACHE_NAME, allEntries = true)
    public DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request) {
        fillCommon(request, true);
        return repository.createDocumentChunk(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = DOCUMENT_CHUNK_CACHE_NAME, allEntries = true)
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request) {
        fillCommon(request, false);
        return repository.updateDocumentChunk(request, currentUserId());
    }

    @Override
    @CacheEvict(cacheNames = DOCUMENT_CHUNK_CACHE_NAME, allEntries = true)
    public void deleteDocumentChunk(String id) {
        repository.deleteDocumentChunk(id, currentUserId());
    }
}
