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
    VERIFY_CODE_EXPIRED(40001, "验证码已过期"),
    VERIFY_CODE_INVALID(40002, "验证码错误"),
    VERIFY_CODE_RATE_LIMITED(40003, "验证码发送过于频繁，请稍后再试"),
    EMAIL_ALREADY_REGISTERED(40004, "该邮箱已被注册"),
    USERNAME_CONFLICT(40005, "用户名已被注册"),
    INTERNAL_ERROR(50000, "系统内部错误"),
    AGENT_ERROR(50001, "Agent 执行异常"),
    DOCUMENT_REFERENCED(50002, "当前资料仍被问答集引用，无法删除"),
    QA_SET_FILE_INVALID(50003, "问答集文件格式不正确");

    private final int code;

    private final String msg;

}
