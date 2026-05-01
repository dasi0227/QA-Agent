package com.dasi.qa.agent.domain.util;

public interface RedisKeyUtil {

    String detail(String prefix, String scope, String id);

    String query(String prefix, String scope, Object request);
}
