package com.dasi.qa.agent.domain.agent.service.generate.model.exception;

import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import lombok.Getter;

@Getter
public class GenerateException extends RuntimeException {

    private final ErrorType errorType;

    public GenerateException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }

}
