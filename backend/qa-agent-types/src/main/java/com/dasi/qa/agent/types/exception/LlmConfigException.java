package com.dasi.qa.agent.types.exception;

import com.dasi.qa.agent.types.enumeration.ResultCode;
import lombok.Getter;

@Getter
public class LlmConfigException extends RuntimeException {

    private final ResultCode resultCode;

    public LlmConfigException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}
