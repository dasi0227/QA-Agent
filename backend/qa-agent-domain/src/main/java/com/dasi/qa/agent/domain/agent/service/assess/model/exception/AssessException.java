package com.dasi.qa.agent.domain.agent.service.assess.model.exception;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import lombok.Getter;

/**
 * 整轮评估链路内部异常，用于携带可映射的错误类型。
 */
@Getter
public class AssessException extends RuntimeException {

    private final ErrorType errorType;

    public AssessException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
