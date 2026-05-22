package com.dasi.qa.agent.domain.agent.service.complete.model.exception;

import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.exception.AgentException;

public class CompleteException extends AgentException {

    public CompleteException(AgentErrorType agentErrorType, String message) {
        super(agentErrorType, message);
    }
}
