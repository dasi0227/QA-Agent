package com.dasi.qa.agent.types.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "操作成功"),
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    INVALID_PARAM(40200, "参数校验失败"),
    FORBIDDEN(40300, "无权访问该资源"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "用户名已被注册"),
    INTERNAL_ERROR(50000, "系统内部错误"),
    VERIFY_CODE_EXPIRED(40001, "验证码已过期"),
    VERIFY_CODE_INVALID(40002, "验证码错误"),
    VERIFY_CODE_RATE_LIMITED(42900, "验证码发送过于频繁，请稍后再试"),
    EMAIL_ALREADY_REGISTERED(40901, "该邮箱已被注册"),
    LLM_NOT_CONFIGURED(40902, "尚未配置大模型"),
    PRACTICE_SESSION_NOT_COMPLETED(40906, "练习尚未全部完成，无法生成评估"),
    DOCUMENT_REFERENCED(40903, "当前资料仍被问答集引用，无法删除");

    private final int code;

    private final String msg;

}
