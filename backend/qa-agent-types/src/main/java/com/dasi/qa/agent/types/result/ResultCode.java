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
    PRACTICE_SESSION_NOT_COMPLETED(40906, "practice session not completed");

    private final int code;

    private final String msg;

}
