package com.dasi.qa.agent.domain.agent.service.generate.model.exception;

import com.dasi.qa.agent.types.exception.AgentException;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;

public class GenerateException extends AgentException {

    public GenerateException(AgentErrorType agentErrorType, String message) {
        super(agentErrorType, message);
    }

}
