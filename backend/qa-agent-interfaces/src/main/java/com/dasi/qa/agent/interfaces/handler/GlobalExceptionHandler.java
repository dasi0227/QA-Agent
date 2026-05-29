package com.dasi.qa.agent.interfaces.handler;

import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.AgentException;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.exception.ConvertException;
import com.dasi.qa.agent.types.exception.LlmConfigException;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleApiException(ApiException exception) {
        log.error("【全局异常】API调用错误: error={}", exception.getMessage(), exception);
        return Result.fail(exception.getResultCode().getCode(), exception.getMessage());
    }

    @ExceptionHandler(ConvertException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleConvertException(ConvertException exception) {
        log.error("【全局异常】文件转换错误: error={}", exception.getMessage(), exception);
        return Result.fail(exception.getResultCode().getCode(), exception.getMessage());
    }

    @ExceptionHandler(LlmConfigException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleLlmConfigException(LlmConfigException exception) {
        log.error("【全局异常】LLM配置错误: error={}", exception.getMessage(), exception);
        return Result.fail(exception.getResultCode().getCode(), exception.getMessage());
    }

    @ExceptionHandler(AgentException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAgentException(AgentException exception) {
        log.error("【全局异常】Agent执行错误: errorType={}, error={}", exception.getAgentErrorType(), exception.getMessage(), exception);
        ResultCode resultCode = ResultCode.of(exception.getAgentErrorType());
        return Result.fail(resultCode.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleInvalidArgument(MethodArgumentNotValidException exception) {
        log.error("【全局异常】参数校验失败: error={}", exception.getMessage(), exception);
        String message = firstValidationMessage(exception.getBindingResult().getAllErrors(), ResultCode.BAD_REQUEST.getMsg());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBindException(BindException exception) {
        log.error("【全局异常】参数绑定失败: error={}", exception.getMessage(), exception);
        String message = firstValidationMessage(exception.getBindingResult().getAllErrors(), ResultCode.INVALID_PARAM.getMsg());
        return Result.fail(ResultCode.INVALID_PARAM.getCode(), message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        log.error("【全局异常】参数约束校验失败: error={}", exception.getMessage(), exception);
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .filter(messageText -> messageText != null && !messageText.isBlank())
                .orElse(ResultCode.INVALID_PARAM.getMsg());
        return Result.fail(ResultCode.INVALID_PARAM.getCode(), message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException exception) {
        log.error("【全局异常】参数处理异常: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.INVALID_PARAM.getCode(), exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        log.error("【全局异常】请求体解析失败: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "请求体格式错误，请检查 JSON 格式");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        log.error("【全局异常】缺少请求参数: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "缺少必要参数：" + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.error("【全局异常】请求参数类型错误: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "参数格式错误：" + exception.getName());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.error("【全局异常】请求方法不支持: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "当前接口不支持该请求方法");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        log.warn("【全局异常】客户端已断开: error={}", exception.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception exception) {
        log.error("【全局异常】未知错误: error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), ResultCode.INTERNAL_ERROR.getMsg());
    }

    private String firstValidationMessage(List<ObjectError> errors, String fallback) {
        if (errors == null || errors.isEmpty()) {
            return fallback;
        }
        return errors.stream()
                .map(ObjectError::getDefaultMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(fallback);
    }

}
