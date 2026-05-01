package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSessionEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSessionItemEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionMapper;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.model.response.BaseResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;

@Repository
public class PracticeRepositoryImpl implements IPracticeRepository {

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;

    public PracticeRepositoryImpl(PracticeSessionMapper practiceSessionMapper, PracticeSessionItemMapper practiceSessionItemMapper) {
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
    }

    @Override
    public PracticeSessionResponse detailPracticeSession(String id, String userId) {
        return detail(practiceSessionMapper, PracticeSessionEntity.class, PracticeSessionResponse.class, id, userId);
    }

    @Override
    public List<PracticeSessionResponse> queryPracticeSession(PracticeSessionRequest request, String userId) {
        return query(practiceSessionMapper, PracticeSessionEntity.class, PracticeSessionResponse.class, request, userId);
    }

    @Override
    public PracticeSessionResponse createPracticeSession(PracticeSessionRequest request, String userId) {
        return create(practiceSessionMapper, PracticeSessionEntity.class, PracticeSessionResponse.class, request);
    }

    @Override
    public PracticeSessionResponse updatePracticeSession(PracticeSessionRequest request, String userId) {
        return update(practiceSessionMapper, PracticeSessionEntity.class, PracticeSessionResponse.class, request);
    }

    @Override
    public void deletePracticeSession(String id, String userId) {
        practiceSessionMapper.deleteById(id);
    }

    @Override
    public PracticeSessionItemResponse detailPracticeSessionItem(String id, String userId) {
        return detail(practiceSessionItemMapper, PracticeSessionItemEntity.class, PracticeSessionItemResponse.class, id, userId);
    }

    @Override
    public List<PracticeSessionItemResponse> queryPracticeSessionItem(PracticeSessionItemRequest request, String userId) {
        return query(practiceSessionItemMapper, PracticeSessionItemEntity.class, PracticeSessionItemResponse.class, request, userId);
    }

    @Override
    public PracticeSessionItemResponse createPracticeSessionItem(PracticeSessionItemRequest request, String userId) {
        return create(practiceSessionItemMapper, PracticeSessionItemEntity.class, PracticeSessionItemResponse.class, request);
    }

    @Override
    public PracticeSessionItemResponse updatePracticeSessionItem(PracticeSessionItemRequest request, String userId) {
        return update(practiceSessionItemMapper, PracticeSessionItemEntity.class, PracticeSessionItemResponse.class, request);
    }

    @Override
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
