package com.dasi.qa.agent.domain.agent.service.feedback.model.exception;

import com.dasi.qa.agent.types.exception.AgentException;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;

/**
 * 单题反馈链路内部异常，用于携带可映射的错误类型。
 */
public class FeedbackException extends AgentException {

    public FeedbackException(AgentErrorType agentErrorType, String message) {
        super(agentErrorType, message);
    }
}
