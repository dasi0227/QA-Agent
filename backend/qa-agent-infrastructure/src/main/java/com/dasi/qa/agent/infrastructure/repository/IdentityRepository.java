package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserAccountEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserProfileEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserAccountMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserProfileMapper;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.model.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.model.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.model.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class IdentityRepository implements IIdentityRepository {

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;

    public IdentityRepository(UserAccountMapper userAccountMapper, UserProfileMapper userProfileMapper) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public UserAccountResponse detailUserAccount(String id, String userId) {
        UserAccountEntity entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return toUserAccountResponse(entity);
    }

    @Override
    public List<UserAccountResponse> queryUserAccount(UserAccountRequest request, String userId) {
        LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<>();
        if (request.getId() != null) {
            wrapper.eq(UserAccountEntity::getId, request.getId());
        }
        if (request.getUsername() != null) {
            wrapper.eq(UserAccountEntity::getUsername, request.getUsername());
        }
        if (request.getEmail() != null) {
            wrapper.eq(UserAccountEntity::getEmail, request.getEmail());
        }
        if (request.getStatus() != null) {
            wrapper.eq(UserAccountEntity::getStatus, request.getStatus());
        }
        return userAccountMapper.selectList(wrapper).stream().map(this::toUserAccountResponse).toList();
    }

    @Override
    public UserAccountResponse createUserAccount(UserAccountRequest request, String userId) {
        UserAccountEntity entity = toEntity(request, UserAccountEntity.class);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.insert(entity);
        return toUserAccountResponse(entity);
    }

    @Override
    public UserAccountResponse updateUserAccount(UserAccountRequest request, String userId) {
        UserAccountEntity entity = toEntity(request, UserAccountEntity.class);
        entity.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(entity);
        return toUserAccountResponse(entity);
    }

    @Override
    public void deleteUserAccount(String id, String userId) {
        UserAccountEntity entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        entity.setStatus("DISABLED");
        entity.setUpdatedAt(LocalDateTime.now());
        userAccountMapper.updateById(entity);
    }

    @Override
    public UserAccountResponse findUserAccountByUsername(String username) {
        UserAccountEntity entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>().eq(UserAccountEntity::getUsername, username));
        return entity == null ? null : toUserAccountResponse(entity);
    }

    @Override
    public UserAccountResponse findUserAccountByEmail(String email) {
        UserAccountEntity entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccountEntity>().eq(UserAccountEntity::getEmail, email));
        return entity == null ? null : toUserAccountResponse(entity);
    }

    @Override
    public UserProfileResponse detailUserProfile(String id, String userId) {
        UserProfileEntity entity = userProfileMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return toUserProfileResponse(entity);
    }

    @Override
    public List<UserProfileResponse> queryUserProfile(UserProfileRequest request, String userId) {
        QueryWrapper<UserProfileEntity> queryWrapper = new QueryWrapper<>();
        Map<String, Object> map = BeanUtil.beanToMap(request, new LinkedHashMap<>(), CopyOptions.create().ignoreNullValue());
        Map<String, Object> snakeMap = new LinkedHashMap<>();
        map.forEach((k, v) -> snakeMap.put(StrUtil.toUnderlineCase(k), v));
        queryWrapper.allEq(snakeMap, false);
        queryWrapper.eq("user_id", userId);
        return userProfileMapper.selectList(queryWrapper).stream().map(this::toUserProfileResponse).toList();
    }

    @Override
    public UserProfileResponse createUserProfile(UserProfileRequest request, String userId) {
        UserProfileEntity entity = toEntity(request, UserProfileEntity.class);
        entity.setUserId(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        userProfileMapper.insert(entity);
        return toUserProfileResponse(entity);
    }

    @Override
    public UserProfileResponse updateUserProfile(UserProfileRequest request, String userId) {
        UserProfileEntity entity = toEntity(request, UserProfileEntity.class);
        entity.setUserId(userId);
        userProfileMapper.updateById(entity);
        return toUserProfileResponse(entity);
    }

    @Override
    public void deleteUserProfile(String id, String userId) {
        userProfileMapper.deleteById(id);
    }

    private <E, Q> E toEntity(Q request, Class<E> entityType) {
        E entity = ReflectUtil.newInstance(entityType);
        BeanUtil.copyProperties(request, entity, CopyOptions.create().ignoreNullValue());
        return entity;
    }

    private UserAccountResponse toUserAccountResponse(UserAccountEntity entity) {
        UserAccountResponse response = ReflectUtil.newInstance(UserAccountResponse.class);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        return response;
    }

    private UserProfileResponse toUserProfileResponse(UserProfileEntity entity) {
        UserProfileResponse response = ReflectUtil.newInstance(UserProfileResponse.class);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        if (response.getId() == null || response.getId().isBlank()) {
            response.setId(entity.getUserId());
        }
        return response;
    }
}
