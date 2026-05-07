package com.dasi.qa.agent.domain.agent.service.generate.model.exception;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;

public class GenerateAbortedException extends GenerateException {

    public GenerateAbortedException(ErrorType errorType, String message) {
        super(errorType, message);
    }
}
