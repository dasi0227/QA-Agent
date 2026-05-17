package com.dasi.qa.agent.types.exception;

import com.dasi.qa.agent.types.enumeration.ResultCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

}
