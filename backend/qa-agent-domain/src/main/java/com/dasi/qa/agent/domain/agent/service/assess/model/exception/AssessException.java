package com.dasi.qa.agent.domain.agent.service.assess.model.exception;

import com.dasi.qa.agent.types.exception.AgentException;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;

/**
 * 整轮评估链路内部异常，用于携带可映射的错误类型。
 */
public class AssessException extends AgentException {

    public AssessException(AgentErrorType agentErrorType, String message) {
        super(agentErrorType, message);
    }
}
