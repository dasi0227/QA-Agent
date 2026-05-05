package com.dasi.qa.agent.domain.util;

public interface IRedisUtil {

    String detail(String prefix, String scope, String id);

    String query(String prefix, String scope, Object request);

    String get(String key);

    void set(String key, String value, long ttlSeconds);

    void delete(String key);
}
