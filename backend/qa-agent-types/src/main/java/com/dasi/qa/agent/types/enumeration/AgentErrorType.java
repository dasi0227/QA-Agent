package com.dasi.qa.agent.types.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentErrorType {
    NETWORK_ERROR("网络连接异常，请稍后重试"),
    RATE_LIMITED("请求过于频繁，请稍后重试"),
    AUTH_FAILURE("LLM 认证失败，请检查 API Key"),
    INVALID_RESPONSE("LLM 返回格式异常"),
    CONTENT_FILTERED("内容已被拦截，请检查输入内容"),
    LLM_NOT_CONFIGURED("尚未配置大模型，请先在个人设置中填写接入信息"),
    PRACTICE_SESSION_NOT_COMPLETED("练习尚未全部完成，无法生成评估"),
    UNKNOWN("未知错误");

    private final String msg;

    public static AgentErrorType fromException(Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        if (message.contains("401") || message.contains("unauthorized") || message.contains("api key")) {
            return AUTH_FAILURE;
        }
        if (message.contains("rate limit") || message.contains("429")) {
            return RATE_LIMITED;
        }
        if (message.contains("parse") || message.contains("json")) {
            return INVALID_RESPONSE;
        }
        if (message.contains("timeout") || message.contains("connect")) {
            return NETWORK_ERROR;
        }
        return UNKNOWN;
    }

}
