package com.dasi.qa.agent.domain.document.service.crud;

import com.dasi.qa.agent.domain.document.model.IndexStatus;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.index.IIndexService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.dto.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentCrudCrudService implements IDocumentCrudService {

    private final IDocumentRepository repository;
    private final IContextUtil contextUtil;
    private final IIndexService indexService;
    private final IIdUtil idUtil;
    private final IMqUtil mqUtil;

    public DocumentCrudCrudService(IDocumentRepository repository,
                                   IContextUtil contextUtil,
                                   IIndexService indexService,
                                   IIdUtil idUtil,
                                   IMqUtil mqUtil) {
        this.repository = repository;
        this.contextUtil = contextUtil;
        this.indexService = indexService;
        this.idUtil = idUtil;
        this.mqUtil = mqUtil;
    }

    @Override
    public SourceDocumentResponse detailSourceDocument(String id) {
        return repository.detailSourceDocument(id, contextUtil.getUserId());
    }

    @Override
    public List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request) {
        return repository.querySourceDocument(request, contextUtil.getUserId());
    }

    @Override
    public SourceDocumentResponse createSourceDocument(SourceDocumentRequest request) {
        String userId = contextUtil.getUserId();
        if (!StringUtils.hasText(request.getId())) {
            request.setId(idUtil.nextId());
        }
        if (!isValidFileType(request.getFileType())) {
            throw new ApiException(ResultCode.FILE_INVALID, "暂不支持该资料类型，请上传 Markdown 资料");
        }
        if (repository.existsSourceDocumentByFileName(request.getFileName(), userId)) {
            throw new ApiException(ResultCode.CONFLICT, "同名资料已存在，请勿重复上传");
        }
        SourceDocumentResponse response = repository.createSourceDocument(request, userId);
        mqUtil.sendIndexMessage(response.getId(), Map.of("documentId", response.getId(), "userId", userId));
        repository.updateIndexStatus(response.getId(), userId, IndexStatus.INDEXING.name());
        return response;
    }

    @Override
    public List<SourceDocumentResponse> listFinishedDocuments() {
        return repository.listFinishedDocuments(contextUtil.getUserId());
    }

    @Override
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request) {
        return repository.updateSourceDocument(request, contextUtil.getUserId());
    }

    @Override
    public void deleteSourceDocument(String id) {
        repository.deleteSourceDocument(id, contextUtil.getUserId());
        indexService.remove(id);
    }

    @Override
    public DocumentChunkResponse detailDocumentChunk(String id) {
        return repository.detailDocumentChunk(id, contextUtil.getUserId());
    }

    @Override
    public List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request) {
        return repository.queryDocumentChunk(request, contextUtil.getUserId());
    }

    @Override
    public DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            request.setId(idUtil.nextId());
        }
        return repository.createDocumentChunk(request, contextUtil.getUserId());
    }

    @Override
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request) {
        return repository.updateDocumentChunk(request, contextUtil.getUserId());
    }

    @Override
    public void deleteDocumentChunk(String id) {
        repository.deleteDocumentChunk(id, contextUtil.getUserId());
    }

    @Override
    public List<DocumentChunkResponse> batchQueryDocumentChunk(List<String> chunkIds) {
        return repository.batchQueryDocumentChunk(chunkIds);
    }

    private static final Set<String> VALID_FILE_TYPES = Set.of("MARKDOWN", "MD");

    private boolean isValidFileType(String fileType) {
        return fileType != null && VALID_FILE_TYPES.contains(fileType.trim().toUpperCase());
    }
}
