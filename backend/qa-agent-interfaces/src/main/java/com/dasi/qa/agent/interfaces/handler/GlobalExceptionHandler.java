package com.dasi.qa.agent.interfaces.handler;

import com.dasi.qa.agent.domain.agent.service.assess.model.exception.AssessException;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.exception.AgentException;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleApiException(ApiException exception) {
        log.error("【全局异常】API调用错误: error={}", exception.getMessage(), exception);
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(AssessException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAssessException(AssessException exception) {
        log.error("【全局异常】Assess执行错误: resultCode={}, error={}",
                exception.getResultCode(), exception.getMessage(), exception);
        return Result.fail(exception.getResultCode().getCode(), exception.getMessage());
    }

    @ExceptionHandler(AgentException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAgentException(AgentException exception) {
        log.error("【全局异常】Agent执行错误: errorType={}, error={}",
                exception.getAgentErrorType(), exception.getMessage(), exception);
        if (exception.getAgentErrorType() == AgentErrorType.LLM_NOT_CONFIGURED) {
            return Result.fail(ResultCode.LLM_NOT_CONFIGURED.getCode(), exception.getMessage());
        }
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleInvalidArgument(Exception exception) {
        log.error("【全局异常】参数校验失败: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), exception.getMessage());
    }

    @ExceptionHandler({BindException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBadRequest(Exception exception) {
        log.error("【全局异常】请求处理异常: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.INVALID_PARAM.getCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception exception) {
        log.error("【全局异常】未知错误: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), exception.getMessage());
    }
}
