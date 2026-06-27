package com.mesh.hello.global.common.exception;

import com.mesh.hello.global.common.response.ErrorCode;
import lombok.Getter;

/**
 * 비즈니스 예외. {@link ErrorCode}를 담아 던지면
 * {@link GlobalExceptionHandler}가 공통 에러 응답으로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
