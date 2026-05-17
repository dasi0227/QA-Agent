package com.dasi.qa.agent.domain.agent.service.feedback.model.exception;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import lombok.Getter;

/**
 * 单题反馈链路内部异常，用于携带可映射的错误类型。
 */
@Getter
public class FeedbackException extends RuntimeException {

    private final ErrorType errorType;

    public FeedbackException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
