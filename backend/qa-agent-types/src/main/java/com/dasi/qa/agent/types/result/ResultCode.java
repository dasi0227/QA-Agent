package com.dasi.qa.agent.types.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(40000, "bad request"),
    UNAUTHORIZED(40100, "unauthorized"),
    INVALID_PARAM(40200, "invalid parameters"),
    FORBIDDEN(40300, "forbidden"),
    NOT_FOUND(40400, "not found"),
    CONFLICT(40900, "username already registered"),
    INTERNAL_ERROR(50000, "internal error"),
    VERIFY_CODE_EXPIRED(40001, "verify code expired"),
    VERIFY_CODE_INVALID(40002, "verify code invalid"),
    VERIFY_CODE_RATE_LIMITED(42900, "verify code rate limited"),
    EMAIL_ALREADY_REGISTERED(40901, "email already registered"),
    LLM_NOT_CONFIGURED(40902, "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name");

    private final int code;

    private final String msg;

}
