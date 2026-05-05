package com.dasi.qa.agent.domain.util;

public interface IMqUtil {

    void send(String topic, String jobId, String content);

    void markSuccess(String jobId);

    void markFail(String jobId);
}
