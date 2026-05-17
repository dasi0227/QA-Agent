package com.dasi.qa.agent.infrastructure.repository;

import static com.dasi.qa.agent.types.constant.StringConstant.DB_USER_ID;

import com.alibaba.fastjson2.JSON;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSession;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSessionItem;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionMapper;
import com.dasi.qa.agent.types.dto.response.practice.AssessDetail;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.dto.response.BaseResponse;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PracticeRepository implements IPracticeRepository {

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;

    public PracticeRepository(PracticeSessionMapper practiceSessionMapper, PracticeSessionItemMapper practiceSessionItemMapper) {
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_DETAIL_KEY, #userId, #id)")
    public PracticeSessionResponse detailPracticeSession(String id, String userId) {
        return detail(practiceSessionMapper, PracticeSession.class, PracticeSessionResponse.class, id, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_QUERY_KEY, #userId, #request)")
    public List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request, String userId) {
        return query(practiceSessionMapper, PracticeSession.class, PracticeSessionResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeSessionResponse createPracticeSession(PracticeSessionRequest request, String userId) {
        return create(practiceSessionMapper, PracticeSession.class, PracticeSessionResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request, String userId) {
        return update(practiceSessionMapper, PracticeSession.class, PracticeSessionResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public void deletePracticeSession(String id, String userId) {
        practiceSessionMapper.deleteById(id);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_ITEM_DETAIL_KEY, #userId, #id)")
    public PracticeSessionItemResponse detailPracticeSessionItem(String id, String userId) {
        return detail(practiceSessionItemMapper, PracticeSessionItem.class, PracticeSessionItemResponse.class, id, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_ITEM_QUERY_KEY, #userId, #request)")
    public List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request, String userId) {
        return query(practiceSessionItemMapper, PracticeSessionItem.class, PracticeSessionItemResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, allEntries = true)
    public PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request, String userId) {
        return create(practiceSessionItemMapper, PracticeSessionItem.class, PracticeSessionItemResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_ITEM_CACHE, allEntries = true)
    public PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request, String userId) {
        return update(practiceSessionItemMapper, PracticeSessionItem.class, PracticeSessionItemResponse.class, request, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)

    public void deletePracticeSessionItem(String id, String userId) {
        practiceSessionItemMapper.deleteById(id);
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
        if (response instanceof PracticeSessionItemResponse itemResponse
                && entity instanceof PracticeSessionItem itemEntity) {
            if (StringUtils.hasText(itemEntity.getFeedbackJudgeDetail())) {
                FeedbackResponse.JudgeDetail judgeDetail = JSON.parseObject(itemEntity.getFeedbackJudgeDetail(), FeedbackResponse.JudgeDetail.class);
                itemResponse.setJudgeDetail(judgeDetail);
            }
            if (StringUtils.hasText(itemEntity.getFeedbackHintDetail())) {
                FeedbackResponse.HintDetail hintDetail = JSON.parseObject(itemEntity.getFeedbackHintDetail(), FeedbackResponse.HintDetail.class);
                itemResponse.setHintDetail(hintDetail);
            }
        }
        if (response instanceof PracticeSessionResponse sessionResponse
                && entity instanceof PracticeSession sessionEntity
                && sessionEntity.getAssessmentDetailJson() != null
                && StringUtils.hasText(sessionEntity.getAssessmentDetailJson())) {
            AssessDetail detail = JSON.parseObject(sessionEntity.getAssessmentDetailJson(), AssessDetail.class);
            sessionResponse.setAssessDetail(detail);
        }
        if (!StringUtils.hasText(response.getId()) && BeanUtil.getProperty(entity, "userId") != null) {
            response.setId(String.valueOf(BeanUtil.getProperty(entity, "userId")));
        }
        return response;
    }
}
