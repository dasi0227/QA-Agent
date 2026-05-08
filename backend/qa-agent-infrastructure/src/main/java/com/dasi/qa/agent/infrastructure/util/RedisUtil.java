package com.dasi.qa.agent.infrastructure.util;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.util.IRedisUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisUtil implements IRedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String detail(String prefix, String scope, String id) {
        return prefix + scope + ":detail:" + id;
    }

    @Override
    public String query(String prefix, String scope, Object request) {
        return prefix + scope + ":query:" + DigestUtil.md5Hex(JSON.toJSONString(request));
    }

    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}
