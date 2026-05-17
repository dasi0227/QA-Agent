package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.*;
import com.dasi.qa.agent.infrastructure.persistent.entity.*;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.BaseResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.dasi.qa.agent.types.constant.StringConstant.DB_USER_ID;

@Repository
public class QaRepository implements IQaRepository {

    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;
    private final QaSetDocumentRefMapper qaSetDocumentRefMapper;

    public QaRepository(QaSetMapper qaSetMapper, QaItemMapper qaItemMapper,
                        PracticeSessionMapper practiceSessionMapper,
                        PracticeSessionItemMapper practiceSessionItemMapper,
                        QaSetDocumentRefMapper qaSetDocumentRefMapper) {
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
        this.qaSetDocumentRefMapper = qaSetDocumentRefMapper;
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_SET_CACHE,
            key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_SET_DETAIL_KEY, #userId, #id)")
    public QaSetResponse detailQaSet(String id, String userId) {
        return detail(qaSetMapper, QaSet.class, QaSetResponse.class, id, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_SET_CACHE,
            key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_SET_QUERY_KEY, #userId, #request)")
    public List<QaSetResponse> queryQaSet(QaSetRequest request, String userId) {
        return query(qaSetMapper, QaSet.class, QaSetResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_SET_CACHE, allEntries = true)
    public QaSetResponse updateQaSet(QaSetRequest request, String userId) {
        return update(qaSetMapper, QaSet.class, QaSetResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = {RedisConstant.QA_SET_CACHE, RedisConstant.QA_ITEM_CACHE}, allEntries = true)
    public void deleteQaSet(String id, String userId) {
        List<PracticeSession> sessions = practiceSessionMapper.selectList(
            new LambdaQueryWrapper<PracticeSession>().eq(PracticeSession::getQaSetId, id));
        for (PracticeSession session : sessions) {
            practiceSessionItemMapper.delete(
                new LambdaQueryWrapper<PracticeSessionItem>().eq(PracticeSessionItem::getSessionId, session.getId()));
        }
        practiceSessionMapper.delete(
            new LambdaQueryWrapper<PracticeSession>().eq(PracticeSession::getQaSetId, id));
        qaItemMapper.delete(
            new LambdaQueryWrapper<QaItem>().eq(QaItem::getQaSetId, id));
        qaSetDocumentRefMapper.delete(
            new LambdaQueryWrapper<QaSetDocumentRef>().eq(QaSetDocumentRef::getQaSetId, id));
        qaSetMapper.deleteById(id);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_ITEM_CACHE,
            key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_ITEM_DETAIL_KEY, #userId, #id)")
    public QaItemResponse detailQaItem(String id, String userId) {
        return detail(qaItemMapper, QaItem.class, QaItemResponse.class, id, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_ITEM_CACHE,
            key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_ITEM_QUERY_KEY, #userId, #request)")
    public List<QaItemResponse> queryQaItem(QaItemRequest request, String userId) {
        return query(qaItemMapper, QaItem.class, QaItemResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public QaItemResponse createQaItem(QaItemRequest request, String userId) {
        return create(qaItemMapper, QaItem.class, QaItemResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public QaItemResponse updateQaItem(QaItemRequest request, String userId) {
        return update(qaItemMapper, QaItem.class, QaItemResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void deleteQaItem(String id, String userId) {
        qaItemMapper.deleteById(id);
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
        Map<String, Object> map = BeanUtil.beanToMap(request, new LinkedHashMap<>(), CopyOptions.create().ignoreNullValue());
        Map<String, Object> snakeMap = new LinkedHashMap<>();
        map.forEach((k, v) -> snakeMap.put(StrUtil.toUnderlineCase(k), v));
        queryWrapper.allEq(snakeMap, false);
        if (ReflectUtil.getField(entityType, "userId") != null) {
            queryWrapper.eq(DB_USER_ID, userId);
        }
        return mapper.selectList(queryWrapper).stream().map(entity -> toResponse(entity, responseType)).toList();
    }

    private <E, Q, R extends BaseResponse> R create(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request, String userId) {
        E entity = toEntity(request, entityType);
        BeanUtil.setProperty(entity, "userId", userId);
        mapper.insert(entity);
        return toResponse(entity, responseType);
    }

    private <E, Q, R extends BaseResponse> R update(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request, String userId) {
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
        if (!StringUtils.hasText(response.getId()) && BeanUtil.getProperty(entity, "userId") != null) {
            response.setId(String.valueOf(BeanUtil.getProperty(entity, "userId")));
        }
        return response;
    }
}
