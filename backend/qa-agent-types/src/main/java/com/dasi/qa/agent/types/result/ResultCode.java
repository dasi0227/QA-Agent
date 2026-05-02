package com.dasi.qa.agent.types.result;

public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(40000, "bad request"),
    UNAUTHORIZED(40100, "unauthorized"),
    FORBIDDEN(40300, "forbidden"),
    NOT_FOUND(40400, "not found"),
    CONFLICT(40900, "conflict"),
    INTERNAL_ERROR(50000, "internal error"),
    VERIFY_CODE_EXPIRED(40001, "verify code expired"),
    VERIFY_CODE_INVALID(40002, "verify code invalid"),
    VERIFY_CODE_RATE_LIMITED(42900, "verify code rate limited"),
    EMAIL_ALREADY_REGISTERED(40901, "email already registered");

    private final int code;

    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
