package com.dasi.qa.agent.types.exception;

import com.dasi.qa.agent.types.enumeration.ResultCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ResultCode resultCode;

    public ApiException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
    }

    public ApiException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

}
