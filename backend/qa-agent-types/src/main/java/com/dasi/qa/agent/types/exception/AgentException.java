package com.dasi.qa.agent.types.exception;

import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import lombok.Getter;

/**
 * Agent 执行异常基类，用于在领域链路和接口异常处理之间传递错误类型。
 */
@Getter
public class AgentException extends RuntimeException {

    private final AgentErrorType agentErrorType;

    public AgentException(AgentErrorType agentErrorType, String message) {
        super(message);
        this.agentErrorType = agentErrorType;
    }
}
