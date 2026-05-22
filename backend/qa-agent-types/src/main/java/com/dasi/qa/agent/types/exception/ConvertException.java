package com.dasi.qa.agent.types.exception;

import com.dasi.qa.agent.types.enumeration.ResultCode;
import lombok.Getter;

@Getter
public class ConvertException extends RuntimeException {
  private final ResultCode resultCode;

  public ConvertException(ResultCode resultCode) {
    super(resultCode.getMsg());
    this.resultCode = resultCode;
  }

  public ConvertException(ResultCode resultCode, String message) {
    super(message);
    this.resultCode = resultCode;
  }
}
