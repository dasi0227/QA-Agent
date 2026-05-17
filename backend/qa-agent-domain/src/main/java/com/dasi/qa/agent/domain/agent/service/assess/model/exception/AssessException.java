package com.dasi.qa.agent.domain.agent.service.assess.model.exception;

import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.AgentException;
import lombok.Getter;

/**
 * assess 链路专属异常，统一承载业务校验和链路状态异常。
 */
@Getter
public class AssessException extends AgentException {

    private final ResultCode resultCode;

    public AssessException(ResultCode resultCode) {
        this(resultCode, resultCode.getMsg());
    }

    public AssessException(ResultCode resultCode, String message) {
        super(AgentErrorType.UNKNOWN, message);
        this.resultCode = resultCode;
    }
}
