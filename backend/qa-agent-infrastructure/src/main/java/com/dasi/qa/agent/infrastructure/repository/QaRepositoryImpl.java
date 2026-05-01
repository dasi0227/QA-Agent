package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaItemEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSetEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.model.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.model.response.BaseResponse;
import com.dasi.qa.agent.types.model.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.model.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;

@Repository
public class QaRepositoryImpl implements IQaRepository {

    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;

    public QaRepositoryImpl(QaSetMapper qaSetMapper, QaItemMapper qaItemMapper) {
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
    }

    @Override
    public QaSetResponse detailQaSet(String id, String userId) {
        return detail(qaSetMapper, QaSetEntity.class, QaSetResponse.class, id, userId);
    }

    @Override
    public List<QaSetResponse> queryQaSet(QaSetRequest request, String userId) {
        return query(qaSetMapper, QaSetEntity.class, QaSetResponse.class, request, userId);
    }

    @Override
    public QaSetResponse createQaSet(QaSetRequest request, String userId) {
        return create(qaSetMapper, QaSetEntity.class, QaSetResponse.class, request);
    }

    @Override
    public QaSetResponse updateQaSet(QaSetRequest request, String userId) {
        return update(qaSetMapper, QaSetEntity.class, QaSetResponse.class, request);
    }

    @Override
    public void deleteQaSet(String id, String userId) {
        qaSetMapper.deleteById(id);
    }

    @Override
    public QaItemResponse detailQaItem(String id, String userId) {
        return detail(qaItemMapper, QaItemEntity.class, QaItemResponse.class, id, userId);
    }

    @Override
    public List<QaItemResponse> queryQaItem(QaItemRequest request, String userId) {
        return query(qaItemMapper, QaItemEntity.class, QaItemResponse.class, request, userId);
    }

    @Override
    public QaItemResponse createQaItem(QaItemRequest request, String userId) {
        return create(qaItemMapper, QaItemEntity.class, QaItemResponse.class, request);
    }

    @Override
    public QaItemResponse updateQaItem(QaItemRequest request, String userId) {
        return update(qaItemMapper, QaItemEntity.class, QaItemResponse.class, request);
    }

    @Override
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
