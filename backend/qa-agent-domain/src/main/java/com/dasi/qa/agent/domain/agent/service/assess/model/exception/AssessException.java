package com.dasi.qa.agent.domain.agent.service.assess.model.exception;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import lombok.Getter;

@Getter
public class AssessException extends RuntimeException {

    private final ErrorType errorType;

    public AssessException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
