package com.dasi.qa.agent.domain.util;

public interface IMqUtil {

    void send(String topic, String jobId, String content);

    void markSuccess(String jobId);

    void markFail(String jobId);

    void recordError(String jobId, String errorMessage);

    void sendIndexMessage(String id, Object content);

    void sendAssistMessage(String id, Object content);
}
