package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.DocumentChunkEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.SourceDocumentEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.DocumentChunkMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.SourceDocumentMapper;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.model.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.model.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.model.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.model.response.BaseResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;

@Repository
public class DocumentRepositoryImpl implements IDocumentRepository {

    private final SourceDocumentMapper sourceDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;

    public DocumentRepositoryImpl(SourceDocumentMapper sourceDocumentMapper, DocumentChunkMapper documentChunkMapper) {
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public SourceDocumentResponse detailSourceDocument(String id, String userId) {
        return detail(sourceDocumentMapper, SourceDocumentEntity.class, SourceDocumentResponse.class, id, userId);
    }

    @Override
    public List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request, String userId) {
        return query(sourceDocumentMapper, SourceDocumentEntity.class, SourceDocumentResponse.class, request, userId);
    }

    @Override
    public SourceDocumentResponse createSourceDocument(SourceDocumentRequest request, String userId) {
        return create(sourceDocumentMapper, SourceDocumentEntity.class, SourceDocumentResponse.class, request);
    }

    @Override
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request, String userId) {
        return update(sourceDocumentMapper, SourceDocumentEntity.class, SourceDocumentResponse.class, request);
    }

    @Override
    public void deleteSourceDocument(String id, String userId) {
        sourceDocumentMapper.deleteById(id);
    }

    @Override
    public DocumentChunkResponse detailDocumentChunk(String id, String userId) {
        return detail(documentChunkMapper, DocumentChunkEntity.class, DocumentChunkResponse.class, id, userId);
    }

    @Override
    public List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request, String userId) {
        return query(documentChunkMapper, DocumentChunkEntity.class, DocumentChunkResponse.class, request, userId);
    }

    @Override
    public DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request, String userId) {
        return create(documentChunkMapper, DocumentChunkEntity.class, DocumentChunkResponse.class, request);
    }

    @Override
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request, String userId) {
        return update(documentChunkMapper, DocumentChunkEntity.class, DocumentChunkResponse.class, request);
    }

    @Override
    public void deleteDocumentChunk(String id, String userId) {
        documentChunkMapper.deleteById(id);
    }

    private <E, R extends BaseResponse> R detail(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, String id, String userId) {
        E entity = mapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (ReflectUtil.getField(entityType, "userId") != null) {
            Object entityUserId = BeanUtil.getProperty(entity, "userId");
            if (entityUserId != null && !userId.equals(String.valueOf(entityUserId))) {
                throw new ApiException(ResultCode.FORBIDDEN);
            }
        }
        return toResponse(entity, responseType);
    }

    private <E, Q, R extends BaseResponse> List<R> query(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request, String userId) {
        QueryWrapper<E> queryWrapper = new QueryWrapper<>();
        queryWrapper.allEq(BeanUtil.beanToMap(request, new LinkedHashMap<>(), CopyOptions.create().ignoreNullValue()), false);
        if (ReflectUtil.getField(entityType, "userId") != null) {
            queryWrapper.eq("user_id", userId);
        }
        return mapper.selectList(queryWrapper).stream().map(entity -> toResponse(entity, responseType)).toList();
    }

    private <E, Q, R extends BaseResponse> R create(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request) {
        E entity = toEntity(request, entityType);
        mapper.insert(entity);
        return toResponse(entity, responseType);
    }

    private <E, Q, R extends BaseResponse> R update(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request) {
        E entity = toEntity(request, entityType);
        mapper.updateById(entity);
        return toResponse(entity, responseType);
    }

    private <E, Q> E toEntity(Q request, Class<E> entityType) {
        E entity = ReflectUtil.newInstance(entityType);
        BeanUtil.copyProperties(request, entity, CopyOptions.create().ignoreNullValue());
        return entity;
    }

    private <E, R extends BaseResponse> R toResponse(E entity, Class<R> responseType) {
        R response = ReflectUtil.newInstance(responseType);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        if ((response.getId() == null || response.getId().isBlank()) && BeanUtil.getProperty(entity, "userId") != null) {
            response.setId(String.valueOf(BeanUtil.getProperty(entity, "userId")));
        }
        return response;
    }
}
