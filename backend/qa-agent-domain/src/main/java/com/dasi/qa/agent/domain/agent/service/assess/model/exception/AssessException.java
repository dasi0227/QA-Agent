package com.dasi.qa.agent.domain.agent.service.assess.model.exception;

import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.exception.AgentException;

/**
 * assess 链路专属异常，统一承载业务校验和链路状态异常。
 */
public class AssessException extends AgentException {

    public AssessException(AgentErrorType agentErrorType, String message) {
        super(agentErrorType, message);
    }
}
