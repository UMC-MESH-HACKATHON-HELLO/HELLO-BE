package com.mesh.hello.global.common.exception;

import com.mesh.hello.global.common.response.ApiResponse;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 컨트롤러 공통 예외 처리. 모든 에러를 {@code {code, message, result:null}} 형식으로 반환한다.
 *
 * <p>HTTP 상태 코드도 {@link ErrorCode#getCode()}와 맞춘다(예: 401 → 401 Unauthorized).</p>
 *
 * <p>주의: 이 핸들러는 HTTP(MVC) 예외만 처리한다. WebSocket(STOMP) {@code @MessageMapping}
 * 예외는 별도의 {@code @MessageExceptionHandler}로 처리해야 한다.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.debug("비즈니스 예외: code={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getCode())
                .body(ApiResponse.error(errorCode.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity
                .status(errorCode.getCode())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
