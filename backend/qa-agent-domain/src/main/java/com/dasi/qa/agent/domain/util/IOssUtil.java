package com.dasi.qa.agent.domain.util;

public interface IOssUtil {

    void upload(byte[] bytes, String objectKey);

    String getPublicUrl(String uri);

    void delete(String uri);
}
