package com.dasi.qa.agent.domain.agent.service.assist.model.exception;

import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.exception.AgentException;

public class AssistException extends AgentException {

    public AssistException(AgentErrorType agentErrorType, String message) {
        super(agentErrorType, message);
    }
}
