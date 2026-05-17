package com.dasi.qa.agent.domain.document.service.crud;

import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.index.IIndexService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentCrudCrudService implements IDocumentCrudService {

    private final IDocumentRepository repository;
    private final IContextUtil contextUtil;
    private final IIndexService indexService;

    public DocumentCrudCrudService(IDocumentRepository repository,
                                   IContextUtil contextUtil,
                                   IIndexService indexService) {
        this.repository = repository;
        this.contextUtil = contextUtil;
        this.indexService = indexService;
    }

    @Override
    public SourceDocumentResponse detailSourceDocument(String id) {
        return repository.detailSourceDocument(id, currentUserId());
    }

    @Override
    public List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request) {
        return repository.querySourceDocument(request, currentUserId());
    }

    @Override
    public SourceDocumentResponse createSourceDocument(SourceDocumentRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            request.setId(UUID.randomUUID().toString());
        }
        return repository.createSourceDocument(request, currentUserId());
    }

    @Override
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request) {
        return repository.updateSourceDocument(request, currentUserId());
    }

    @Override
    public void deleteSourceDocument(String id) {
        repository.deleteSourceDocument(id, currentUserId());
        indexService.remove(id);
    }

    @Override
    public DocumentChunkResponse detailDocumentChunk(String id) {
        return repository.detailDocumentChunk(id, currentUserId());
    }

    @Override
    public List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request) {
        return repository.queryDocumentChunk(request, currentUserId());
    }

    @Override
    public DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            request.setId(UUID.randomUUID().toString());
        }
        return repository.createDocumentChunk(request, currentUserId());
    }

    @Override
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request) {
        return repository.updateDocumentChunk(request, currentUserId());
    }

    @Override
    public void deleteDocumentChunk(String id) {
        repository.deleteDocumentChunk(id, currentUserId());
    }

    private String currentUserId() {
        return contextUtil.getUserId();
    }
}
