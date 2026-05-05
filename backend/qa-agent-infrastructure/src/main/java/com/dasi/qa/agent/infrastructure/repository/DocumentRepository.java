package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.document.model.ChunkDraft;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class DocumentRepository implements IDocumentRepository {

    private final SourceDocumentMapper sourceDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final JdbcTemplate postgresJdbc;

    public DocumentRepository(SourceDocumentMapper sourceDocumentMapper,
                              DocumentChunkMapper documentChunkMapper,
                              @Qualifier("postgresDataSource") DataSource postgresDataSource) {
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.postgresJdbc = new JdbcTemplate(postgresDataSource);
    }

    // ======================== existing methods ========================

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
        return create(sourceDocumentMapper, SourceDocumentEntity.class, SourceDocumentResponse.class, request, userId);
    }

    @Override
    public SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request, String userId) {
        return update(sourceDocumentMapper, SourceDocumentEntity.class, SourceDocumentResponse.class, request, userId);
    }

    @Override
    public void deleteSourceDocument(String id, String userId) {
        SourceDocumentEntity entity = sourceDocumentMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        entity.setDeleted(true);
        sourceDocumentMapper.updateById(entity);
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
        return create(documentChunkMapper, DocumentChunkEntity.class, DocumentChunkResponse.class, request, userId);
    }

    @Override
    public DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request, String userId) {
        return update(documentChunkMapper, DocumentChunkEntity.class, DocumentChunkResponse.class, request, userId);
    }

    @Override
    public void deleteDocumentChunk(String id, String userId) {
        documentChunkMapper.deleteById(id);
    }

    // ======================== V2 RAG: MySQL batch ========================

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void replaceDocumentChunks(String documentId, String userId, List<ChunkDraft> drafts) {
        // delete old chunks
        QueryWrapper<DocumentChunkEntity> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("document_id", documentId);
        documentChunkMapper.delete(deleteWrapper);

        // insert new chunks
        for (ChunkDraft draft : drafts) {
            DocumentChunkEntity entity = new DocumentChunkEntity();
            entity.setId(draft.getChunkId() != null ? draft.getChunkId() : UUID.randomUUID().toString());
            entity.setDocumentId(documentId);
            entity.setUserId(userId);
            entity.setChunkIndex(draft.getChunkIndex());
            entity.setTitlePath(draft.getTitlePath());
            entity.setContent(draft.getContent());
            entity.setSummary(draft.getSummary());
            if (draft.getModuleTags() != null && !draft.getModuleTags().isEmpty()) {
                entity.setModuleTagsJson(toJsonArray(draft.getModuleTags()));
            }
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            documentChunkMapper.insert(entity);
        }
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void deleteDocumentChunksByDocumentId(String documentId) {
        QueryWrapper<DocumentChunkEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId);
        documentChunkMapper.delete(wrapper);
    }

    @Override
    public String getDocumentUserId(String documentId) {
        SourceDocumentEntity entity = sourceDocumentMapper.selectById(documentId);
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
                    title_path, content, summary, module_tags_json, embedding, content_tsv,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, to_tsvector('zh', ?), NOW(), NOW())
                """;
        List<Object[]> batchArgs = new ArrayList<>();
        for (ChunkSearchRow row : rows) {
            batchArgs.add(new Object[]{
                    row.getChunkId(),
                    row.getDocumentId(),
                    row.getUserId(),
                    row.getChunkIndex(),
                    row.getTitlePath(),
                    row.getContent(),
                    row.getSummary(),
                    toJsonArray(row.getModuleTags()),
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
            List<String> docIds, List<String> tags, String pathPrefix, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT chunk_id, document_id, user_id, chunk_index, title_path,
                       content, summary, module_tags_json,
                       1 - (embedding <=> ?::vector) AS vector_score,
                       0 AS keyword_score
                FROM chunk_search
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(vectorToString(queryVector));
        params.add(userId);
        appendFilters(sql, params, docIds, tags, pathPrefix);
        sql.append(" ORDER BY embedding <=> ?::vector LIMIT ?");
        params.add(vectorToString(queryVector));
        params.add(limit);

        return postgresJdbc.query(sql.toString(), new ChunkSearchRowMapper(), params.toArray());
    }

    @Override
    public List<ChunkSearchRow> keywordSearch(String queryText, String userId,
            List<String> docIds, List<String> tags, String pathPrefix, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT chunk_id, document_id, user_id, chunk_index, title_path,
                       content, summary, module_tags_json,
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
        appendFilters(sql, params, docIds, tags, pathPrefix);
        sql.append(" ORDER BY keyword_score DESC LIMIT ?");
        params.add(limit);

        return postgresJdbc.query(sql.toString(), new ChunkSearchRowMapper(), params.toArray());
    }

    private void appendFilters(StringBuilder sql, List<Object> params,
                               List<String> docIds, List<String> tags, String pathPrefix) {
        if (docIds != null && !docIds.isEmpty()) {
            sql.append(" AND document_id = ANY(?::varchar[])");
            params.add(postgresJdbc.getDataSource() != null
                    ? createSqlArray(docIds) : docIds.toArray(new String[0]));
        }
        if (tags != null && !tags.isEmpty()) {
            sql.append(" AND module_tags_json @> ?::jsonb");
            params.add(toJsonArray(tags));
        }
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            sql.append(" AND title_path LIKE ?");
            params.add(pathPrefix + "%");
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
        if (queryText == null || queryText.isBlank()) return "";
        String[] words = queryText.trim().split("\\s+");
        return String.join(" & ", words);
    }

    private java.sql.Array createSqlArray(List<String> items) {
        try {
            return postgresJdbc.getDataSource().getConnection()
                    .createArrayOf("varchar", items.toArray());
        } catch (SQLException e) {
            throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Failed to create SQL array: " + e.getMessage());
        }
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
            queryWrapper.eq("user_id", userId);
        }
        if (ReflectUtil.getField(entityType, "deleted") != null) {
            queryWrapper.eq("deleted", false);
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
        if ((response.getId() == null || response.getId().isBlank())
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
            row.setTitlePath(rs.getString("title_path"));
            row.setContent(rs.getString("content"));
            row.setSummary(rs.getString("summary"));
            String tagsJson = rs.getString("module_tags_json");
            if (tagsJson != null) {
                row.setModuleTags(parseJsonArray(tagsJson));
            } else {
                row.setModuleTags(List.of());
            }
            row.setVectorScore((float) rs.getDouble("vector_score"));
            row.setKeywordScore((float) rs.getDouble("keyword_score"));
            return row;
        }

        private List<String> parseJsonArray(String json) {
            if (json == null || json.isBlank() || "[]".equals(json)) {
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
