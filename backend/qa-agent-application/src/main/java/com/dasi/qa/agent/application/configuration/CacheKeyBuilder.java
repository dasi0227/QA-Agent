package com.dasi.qa.agent.application.configuration;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

@Component("cacheKeyBuilder")
public class CacheKeyBuilder {

    public String detail(String resource, String userId, String id) {
        return build(resource, userId, "detail", id);
    }

    public String query(String resource, String userId, Object command) {
        return build(resource, userId, "query", DigestUtil.md5Hex(JSON.toJSONString(command)));
    }

    public String build(String resource, String userId, String action, String value) {
        return StrUtil.join(":", resource, userId, action, value);
    }
}
