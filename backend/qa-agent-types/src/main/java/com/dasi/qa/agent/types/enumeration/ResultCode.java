package com.dasi.qa.agent.types.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "操作成功"),
    BAD_REQUEST(40000, "请求参数错误"),
    INVALID_PARAM(40200, "参数校验失败"),
    VERIFY_CODE_EXPIRED(40001, "验证码已过期"),
    VERIFY_CODE_INVALID(40002, "验证码错误"),
    VERIFY_CODE_RATE_LIMITED(40003, "验证码发送过于频繁，请稍后再试"),
    EMAIL_ALREADY_REGISTERED(40004, "该邮箱已被注册"),
    USERNAME_CONFLICT(40005, "用户名已被注册"),
    PASSWORD_INVALID(40006, "当前密码错误"),
    FILE_INVALID(40010, "文件格式不正确"),
    QA_SET_FILE_INVALID(40011, "问答集文件格式不正确"),
    PRACTICE_NOT_READY(40020, "练习状态不满足当前操作"),
    LLM_NOT_CONFIGURED(40030, "模型配置未完成"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "当前操作不可用"),
    ACCOUNT_DISABLED(40301, "账号当前不可用"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源状态冲突"),
    RESOURCE_IN_USE(40910, "资源正在被使用"),
    INTERNAL_ERROR(50000, "系统内部错误"),
    AGENT_ERROR(50001, "Agent 执行异常"),
    AGENT_RESPONSE_INVALID(50002, "Agent 返回格式异常"),
    EXTERNAL_SERVICE_UNAVAILABLE(50300, "外部服务暂时不可用");

    private final int code;

    private final String msg;

    public static ResultCode of(AgentErrorType agentErrorType) {
        if (agentErrorType == null) {
            return AGENT_ERROR;
        }
        return switch (agentErrorType) {
            case LLM_NOT_CONFIGURED -> LLM_NOT_CONFIGURED;
            case PRACTICE_SESSION_NOT_COMPLETED -> PRACTICE_NOT_READY;
            case INVALID_RESPONSE -> AGENT_RESPONSE_INVALID;
            case NETWORK_ERROR, RATE_LIMITED, AUTH_FAILURE -> EXTERNAL_SERVICE_UNAVAILABLE;
            case CONTENT_FILTERED -> BAD_REQUEST;
            case UNKNOWN -> AGENT_ERROR;
        };
    }

}
