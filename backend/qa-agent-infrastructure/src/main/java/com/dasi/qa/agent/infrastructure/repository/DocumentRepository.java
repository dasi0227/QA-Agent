package com.dasi.qa.agent.infrastructure.repository;

import static com.dasi.qa.agent.types.constant.StringConstant.DB_DELETED;
import static com.dasi.qa.agent.types.constant.StringConstant.DB_USER_ID;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.document.model.ChunkDraft;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.DocumentChunk;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSet;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSetDocumentRef;
import com.dasi.qa.agent.infrastructure.persistent.entity.SourceDocument;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.DocumentChunkMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetDocumentRefMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.SourceDocumentMapper;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.dto.response.BaseResponse;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class DocumentRepository implements IDocumentRepository {

    private final SourceDocumentMapper sourceDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final QaSetMapper qaSetMapper;
    private final QaSetDocumentRefMapper qaSetDocumentRefMapper;
    private final JdbcTemplate postgresJdbc;
    private final IIdUtil idUtil;

    public DocumentRepository(SourceDocumentMapper sourceDocumentMapper,
                              DocumentChunkMapper documentChunkMapper,
                              QaSetMapper qaSetMapper,
                              QaSetDocumentRefMapper qaSetDocumentRefMapper,
                              @Qualifier("postgresDataSource") DataSource postgresDataSource,
                              IIdUtil idUtil) {
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.qaSetMapper = qaSetMapper;
        this.qaSetDocumentRefMapper = qaSetDocumentRefMapper;
        this.postgresJdbc = new JdbcTemplate(postgresDataSource);
        this.idUtil = idUtil;
    }

    // ======================== existing methods ========================

    @Override
    @Cacheable(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_SOURCE_DOCUMENT_DETAIL_KEY, #userId, #id)")
    public SourceDocumentResponse detailSourceDocument(String id, String userId) {
        return detail(sourceDocumentMapper, SourceDocument.class, SourceDocumentResponse.class, id, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_SOURCE_DOCUMENT_QUERY_KEY, #userId, #request)")
    public List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request, String userId) {
        return query(sourceDocumentMapper, SourceDocument.class, SourceDocumentResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, allEntries = true)
    public SourceDocumentResponse createSourceDocument(SourceDocumentRequest request, String userId) {
        return create(sourceDocumentMapper, SourceDocument.class, SourceDocumentResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, allEntries = true)
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request, String userId) {
        SourceDocument entity = sourceDocumentMapper.selectById(request.getId());
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        entity.setFileName(request.getFileName());
        sourceDocumentMapper.updateById(entity);
        return toResponse(entity, SourceDocumentResponse.class);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_SOURCE_DOCUMENT_CACHE, allEntries = true)
    public void deleteSourceDocument(String id, String userId) {
        SourceDocument entity = sourceDocumentMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        if (entity.getReferenceCount() != null && entity.getReferenceCount() > 0) {
            List<String> qaSetIds = qaSetDocumentRefMapper.selectList(
                    new LambdaQueryWrapper<QaSetDocumentRef>().eq(QaSetDocumentRef::getDocumentId, id))
                    .stream().map(QaSetDocumentRef::getQaSetId).distinct().toList();
            String titles = qaSetMapper.selectBatchIds(qaSetIds).stream()
                    .map(QaSet::getTitle).filter(StringUtils::hasText)
                    .reduce((a, b) -> a + "、 " + b).orElse("");
            throw new ApiException(ResultCode.DOCUMENT_REFERENCED,
                    "当前资料仍被以下问答集引用，无法删除：" + titles);
        }
        entity.setDeleted(true);
        sourceDocumentMapper.updateById(entity);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_CHUNK_DETAIL_KEY, #userId, #id)")
    public DocumentChunkResponse detailDocumentChunk(String id, String userId) {
        return detail(documentChunkMapper, DocumentChunk.class, DocumentChunkResponse.class, id, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).DOCUMENT_CHUNK_QUERY_KEY, #userId, #request)")
    public List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request, String userId) {
        return query(documentChunkMapper, DocumentChunk.class, DocumentChunkResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request, String userId) {
        return create(documentChunkMapper, DocumentChunk.class, DocumentChunkResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request, String userId) {
        return update(documentChunkMapper, DocumentChunk.class, DocumentChunkResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public void deleteDocumentChunk(String id, String userId) {
        documentChunkMapper.deleteById(id);
    }

    @Override
    public List<DocumentChunkResponse> batchQueryDocumentChunk(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> chunks = documentChunkMapper.selectBatchIds(chunkIds);
        List<String> documentIds = chunks.stream()
                .map(DocumentChunk::getDocumentId)
                .distinct()
                .toList();
        Map<String, String> fileNameMap = sourceDocumentMapper.selectBatchIds(documentIds).stream()
                .collect(Collectors.toMap(SourceDocument::getId, SourceDocument::getFileName, (a, b) -> a));
        return chunks.stream()
                .map(entity -> {
                    DocumentChunkResponse response = toResponse(entity, DocumentChunkResponse.class);
                    response.setFileName(fileNameMap.getOrDefault(entity.getDocumentId(), ""));
                    return response;
                })
                .toList();
    }

    // ======================== V2 RAG: MySQL batch ========================

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void replaceDocumentChunks(String documentId, String userId, List<ChunkDraft> drafts) {
        // delete old chunks
        LambdaQueryWrapper<DocumentChunk> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DocumentChunk::getDocumentId, documentId);
        documentChunkMapper.delete(deleteWrapper);

        // insert new chunks
        for (ChunkDraft draft : drafts) {
            DocumentChunk entity = new DocumentChunk();
            entity.setId(draft.getChunkId() != null ? draft.getChunkId() : idUtil.nextId());
            entity.setDocumentId(documentId);
            entity.setUserId(userId);
            entity.setChunkIndex(draft.getChunkIndex());
            entity.setHeadingPath(draft.getHeadingPath());
            entity.setContent(draft.getContent());
            entity.setSummary(draft.getSummary());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            documentChunkMapper.insert(entity);
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = RedisConstant.DOCUMENT_CHUNK_CACHE, allEntries = true)
    public void deleteDocumentChunksByDocumentId(String documentId) {
        LambdaQueryWrapper<DocumentChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunk::getDocumentId, documentId);
        documentChunkMapper.delete(wrapper);
    }

    @Override
    public String getDocumentUserId(String documentId) {
        SourceDocument entity = sourceDocumentMapper.selectById(documentId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return entity.getUserId();
    }

    // ======================== V2 RAG: PostgreSQL chunk_search ========================

    @Override
    @Transactional(transactionManager = "postgresTransactionManager")
    public void batchInsertChunkSearch(List<ChunkSearchRow> rows) {
        String sql = """
                INSERT INTO chunk_search (chunk_id, document_id, user_id, chunk_index,
                    heading_path, content, summary, embedding, content_tsv,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, to_tsvector('zh', ?), NOW(), NOW())
                """;
        List<Object[]> batchArgs = new ArrayList<>();
        for (ChunkSearchRow row : rows) {
            batchArgs.add(new Object[]{
                    row.getChunkId(),
                    row.getDocumentId(),
                    row.getUserId(),
                    row.getChunkIndex(),
                    row.getHeadingPath(),
                    row.getContent(),
                    row.getSummary(),
                    vectorToString(row.getEmbedding()),
                    row.getContent()
            });
        }
        postgresJdbc.batchUpdate(sql, batchArgs);
    }

    @Override
    @Transactional(transactionManager = "postgresTransactionManager")
    public void deleteChunkSearchByDocumentId(String documentId) {
        postgresJdbc.update("DELETE FROM chunk_search WHERE document_id = ?", documentId);
    }

    @Override
    public List<ChunkSearchRow> semanticSearch(float[] queryVector, String userId,
            List<String> docIds, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT chunk_id, document_id, user_id, chunk_index, heading_path,
                       content, summary,
                       1 - (embedding <=> ?::vector) AS vector_score,
                       0 AS keyword_score
                FROM chunk_search
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(vectorToString(queryVector));
        params.add(userId);
        appendDocumentFilter(sql, params, docIds);
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(vectorToString(queryVector));
        params.add(limit);

        return postgresJdbc.query(sql.toString(), new ChunkSearchRowMapper(), params.toArray());
    }

    @Override
    public List<ChunkSearchRow> keywordSearch(String queryText, String userId,
            List<String> docIds, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT chunk_id, document_id, user_id, chunk_index, heading_path,
                       content, summary,
                       0 AS vector_score,
                       ts_rank(content_tsv, to_tsquery('zh', ?)) AS keyword_score
                FROM chunk_search
                WHERE user_id = ?
                  AND content_tsv @@ to_tsquery('zh', ?)
                """);
        List<Object> params = new ArrayList<>();
        params.add(toTsquery(queryText));
        params.add(userId);
        params.add(toTsquery(queryText));
        appendDocumentFilter(sql, params, docIds);
        sql.append(" ORDER BY keyword_score DESC LIMIT ?");
        params.add(limit);

        return postgresJdbc.query(sql.toString(), new ChunkSearchRowMapper(), params.toArray());
    }

    private void appendDocumentFilter(StringBuilder sql, List<Object> params,
                                      List<String> docIds) {
        if (docIds != null && !docIds.isEmpty()) {
            sql.append(" AND document_id = ANY(?::varchar[])");
            params.add(docIds.toArray(new String[0]));
        }
    }

    // ======================== private helpers ========================

    private String vectorToString(float[] vector) {
        if (vector == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Convert user query text to tsquery format.
     * Splits on whitespace and joins with & (AND).
     */
    private String toTsquery(String queryText) {
        if (!StringUtils.hasText(queryText)) return "";
        String[] words = queryText.trim().split("\\s+");
        return String.join(" & ", words);
    }

    // ======================== generic helpers (existing) ========================

    private <E, R extends BaseResponse> R detail(BaseMapper<E> mapper, Class<E> entityType,
                                                  Class<R> responseType, String id, String userId) {
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

    private <E, Q, R extends BaseResponse> List<R> query(BaseMapper<E> mapper, Class<E> entityType,
            Class<R> responseType, Q request, String userId) {
        QueryWrapper<E> queryWrapper = new QueryWrapper<>();
        Map<String, Object> map = BeanUtil.beanToMap(request, new LinkedHashMap<>(),
                CopyOptions.create().ignoreNullValue());
        Map<String, Object> snakeMap = new LinkedHashMap<>();
        map.forEach((k, v) -> snakeMap.put(StrUtil.toUnderlineCase(k), v));
        queryWrapper.allEq(snakeMap, false);
        if (ReflectUtil.getField(entityType, "userId") != null) {
            queryWrapper.eq(DB_USER_ID, userId);
        }
        if (ReflectUtil.getField(entityType, "deleted") != null) {
            queryWrapper.eq(DB_DELETED, false);
        }
        return mapper.selectList(queryWrapper).stream()
                .map(entity -> toResponse(entity, responseType)).toList();
    }

    private <E, Q, R extends BaseResponse> R create(BaseMapper<E> mapper, Class<E> entityType,
            Class<R> responseType, Q request, String userId) {
        E entity = toEntity(request, entityType);
        BeanUtil.setProperty(entity, "userId", userId);
        mapper.insert(entity);
        return toResponse(entity, responseType);
    }

    private <E, Q, R extends BaseResponse> R update(BaseMapper<E> mapper, Class<E> entityType,
            Class<R> responseType, Q request, String userId) {
        E entity = toEntity(request, entityType);
        BeanUtil.setProperty(entity, "userId", userId);
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
        if (!StringUtils.hasText(response.getId())
                && BeanUtil.getProperty(entity, "userId") != null) {
            response.setId(String.valueOf(BeanUtil.getProperty(entity, "userId")));
        }
        return response;
    }

    // ======================== inner row mapper ========================

    private static class ChunkSearchRowMapper implements RowMapper<ChunkSearchRow> {
        @Override
        public ChunkSearchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChunkSearchRow row = new ChunkSearchRow();
            row.setChunkId(rs.getString("chunk_id"));
            row.setDocumentId(rs.getString("document_id"));
            row.setUserId(rs.getString("user_id"));
            row.setChunkIndex(rs.getInt("chunk_index"));
            row.setHeadingPath(rs.getString("heading_path"));
            row.setContent(rs.getString("content"));
            row.setSummary(rs.getString("summary"));
            row.setVectorScore((float) rs.getDouble("vector_score"));
            row.setKeywordScore((float) rs.getDouble("keyword_score"));
            return row;
        }

        private List<String> parseJsonArray(String json) {
            if (!StringUtils.hasText(json) || "[]".equals(json)) {
                return List.of();
            }
            // simple JSON array parser: ["a","b"] → [a, b]
            String stripped = json.trim();
            if (stripped.startsWith("[") && stripped.endsWith("]")) {
                stripped = stripped.substring(1, stripped.length() - 1);
            }
            List<String> result = new ArrayList<>();
            for (String part : stripped.split(",")) {
                String cleaned = part.trim().replaceAll("^\"|\"$", "");
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }
            return result;
        }
    }
}
