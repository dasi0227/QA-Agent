package com.dasi.qa.agent.infrastructure.util;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import org.springframework.stereotype.Component;

@Component("redisKeyUtil")
public class RedisKeyUtil implements com.dasi.qa.agent.domain.util.RedisKeyUtil {

    @Override
    public String detail(String prefix, String scope, String id) {
        return prefix + scope + ":detail:" + id;
    }

    @Override
    public String query(String prefix, String scope, Object request) {
        return prefix + scope + ":query:" + DigestUtil.md5Hex(JSON.toJSONString(request));
    }
}
