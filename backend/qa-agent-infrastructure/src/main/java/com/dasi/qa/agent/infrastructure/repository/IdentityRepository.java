package com.dasi.qa.agent.infrastructure.repository;

import static com.dasi.qa.agent.types.constant.StringConstant.DB_USER_ID;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dasi.qa.agent.types.constant.RedisConstant;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.dasi.qa.agent.domain.identity.repository.IIdentityRepository;
import com.dasi.qa.agent.domain.identity.model.enumeration.AccountStatus;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserAccount;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserProfile;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserAccountMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserProfileMapper;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.dto.request.identity.UserAccountRequest;
import com.dasi.qa.agent.types.dto.request.identity.UserProfileRequest;
import com.dasi.qa.agent.types.dto.response.identity.UserAccountResponse;
import com.dasi.qa.agent.types.dto.response.identity.UserProfileResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

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
    @Cacheable(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE,
            key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_ACCOUNT_DETAIL_KEY, 'self', #id)")
    public UserAccountResponse detailUserAccount(String id, String userId) {
        UserAccount entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "账号不存在");
        }
        return toUserAccountResponse(entity);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE,
            key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_ACCOUNT_QUERY_KEY, #userId, #request)")
    public List<UserAccountResponse> queryUserAccount(UserAccountRequest request, String userId) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        if (request.getId() != null) {
            wrapper.eq(UserAccount::getId, request.getId());
        }
        if (request.getUsername() != null) {
            wrapper.eq(UserAccount::getUsername, request.getUsername());
        }
        if (request.getEmail() != null) {
            wrapper.eq(UserAccount::getEmail, request.getEmail());
        }
        if (request.getStatus() != null) {
            wrapper.eq(UserAccount::getStatus, request.getStatus());
        }
        return userAccountMapper.selectList(wrapper).stream().map(this::toUserAccountResponse).toList();
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    public UserAccountResponse createUserAccount(UserAccountRequest request, String userId) {
        UserAccount entity = toEntity(request, UserAccount.class);
        userAccountMapper.insert(entity);
        return toUserAccountResponse(entity);
    }

    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    @Override
    public UserAccountResponse updateUserAccount(UserAccountRequest request, String userId) {
        UserAccount entity = toEntity(request, UserAccount.class);
        userAccountMapper.updateById(entity);
        return toUserAccountResponse(entity);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    public void updatePassword(String userId, String encodedPassword) {
        UserAccount entity = new UserAccount();
        entity.setId(userId);
        entity.setPassword(encodedPassword);
        userAccountMapper.updateById(entity);
    }

    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_ACCOUNT_CACHE, allEntries = true)
    public void deleteUserAccount(String id, String userId) {
        UserAccount entity = userAccountMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "账号不存在");
        }
        entity.setStatus(AccountStatus.DISABLED.name());
        userAccountMapper.updateById(entity);
    }

    @Override
    public UserAccountResponse findUserAccountByUsername(String username) {
        UserAccount entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username));
        return entity == null ? null : toUserAccountResponse(entity);
    }

    @Override
    public UserAccountResponse findUserAccountByEmail(String email) {
        UserAccount entity = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getEmail, email));
        return entity == null ? null : toUserAccountResponse(entity);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE,
            key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_PROFILE_DETAIL_KEY, #userId, #id)")
    public UserProfileResponse detailUserProfile(String id, String userId) {
        UserProfile entity = userProfileMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "个人资料不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "个人资料不存在");
        }
        return toUserProfileResponse(entity);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE,
            key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).IDENTITY_USER_PROFILE_QUERY_KEY, #userId, #request)")
    public List<UserProfileResponse> queryUserProfile(UserProfileRequest request, String userId) {
        QueryWrapper<UserProfile> queryWrapper = new QueryWrapper<>();
        Map<String, Object> map = BeanUtil.beanToMap(request, new LinkedHashMap<>(), CopyOptions.create().ignoreNullValue());
        Map<String, Object> snakeMap = new LinkedHashMap<>();
        map.forEach((k, v) -> snakeMap.put(StrUtil.toUnderlineCase(k), v));
        queryWrapper.allEq(snakeMap, false);
        queryWrapper.eq(DB_USER_ID, userId);
        return userProfileMapper.selectList(queryWrapper).stream().map(this::toUserProfileResponse).toList();
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE, allEntries = true)
    public UserProfileResponse createUserProfile(UserProfileRequest request, String userId) {
        UserProfile entity = toEntity(request, UserProfile.class);
        entity.setUserId(userId);
        userProfileMapper.insert(entity);
        return toUserProfileResponse(entity);
    }

    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE, allEntries = true)
    @Override
    public UserProfileResponse updateUserProfile(UserProfileRequest request, String userId) {
        UserProfile entity = toEntity(request, UserProfile.class);
        entity.setUserId(userId);
        userProfileMapper.updateById(entity);
        return toUserProfileResponse(entity);
    }

    @CacheEvict(cacheNames = RedisConstant.IDENTITY_USER_PROFILE_CACHE, allEntries = true)
    public void deleteUserProfile(String id, String userId) {
        userProfileMapper.deleteById(id);
    }

    private <E, Q> E toEntity(Q request, Class<E> entityType) {
        E entity = ReflectUtil.newInstance(entityType);
        BeanUtil.copyProperties(request, entity, CopyOptions.create().ignoreNullValue());
        return entity;
    }

    private UserAccountResponse toUserAccountResponse(UserAccount entity) {
        UserAccountResponse response = ReflectUtil.newInstance(UserAccountResponse.class);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        return response;
    }

    private UserProfileResponse toUserProfileResponse(UserProfile entity) {
        UserProfileResponse response = ReflectUtil.newInstance(UserProfileResponse.class);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        if (!StringUtils.hasText(response.getId())) {
            response.setId(entity.getUserId());
        }
        return response;
    }
}
