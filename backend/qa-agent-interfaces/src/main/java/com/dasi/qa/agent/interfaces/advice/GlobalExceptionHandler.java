package com.dasi.qa.agent.interfaces.advice;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.Result;
import com.dasi.qa.agent.types.result.ResultCode;
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
        log.error("error={}", exception.getMessage(), exception);
        return Result.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBadRequest(Exception exception) {
        log.error("error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleException(Exception exception) {
        log.error("error={}", exception.getMessage(), exception);
        return Result.fail(ResultCode.INTERNAL_ERROR.getCode(), exception.getMessage());
    }
}
