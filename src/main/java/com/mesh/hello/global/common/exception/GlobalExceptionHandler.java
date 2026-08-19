package com.mesh.hello.global.common.exception;

import com.mesh.hello.global.common.response.ApiResponse;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

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

    /**
     * Bean Validation 실패 핸들러 (AC101-2 — 형식 오류).
     *
     * <p>DTO 필드의 {@code message} 속성에 {@link ErrorCode} 상수 이름을 문자열로 적어두면,
     * 여기서 {@code ErrorCode.valueOf(defaultMessage)}로 되돌린다.
     * 여러 필드가 동시에 실패했을 때는 {@link ErrorCode#ordinal()}이 가장 작은 것(입력칸
     * 순서상 가장 앞)을 대표로 선택해 응답한다.</p>
     *
     * <p>valueOf 변환 실패(알 수 없는 message 값)는 {@code INTERNAL_ERROR}로 폴백한다.</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();

        ErrorCode representative = fieldErrors.stream()
                .map(fe -> {
                    try {
                        return ErrorCode.valueOf(fe.getDefaultMessage());
                    } catch (IllegalArgumentException | NullPointerException ex) {
                        log.warn("FieldError message를 ErrorCode로 변환할 수 없습니다: field={}, message={}",
                                fe.getField(), fe.getDefaultMessage());
                        return null;
                    }
                })
                .filter(ec -> ec != null)
                .min(Comparator.comparingInt(ErrorCode::ordinal))
                .orElse(ErrorCode.INTERNAL_ERROR);

        return ResponseEntity
                .status(representative.getCode())
                .body(ApiResponse.error(representative.getCode(), representative.getMessage()));
    }

    /**
     * DB unique 제약 위반 핸들러 (AC101-1 — 중복).
     *
     * <p>예외 원문은 응답에 노출하지 않고 {@code log.warn}으로만 기록한다.
     * 메시지에 "email" 또는 "uq_users_email"이 포함되면 이메일 중복,
     * 그 외에는 사용자명 중복으로 판단한다.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 위반: {}", e.getMostSpecificCause().getMessage());

        String msg = e.getMostSpecificCause().getMessage();
        boolean isEmailDuplicate = msg != null &&
                (msg.toLowerCase().contains("email") || msg.toLowerCase().contains("uq_users_email"));

        ErrorCode errorCode = isEmailDuplicate ? ErrorCode.DUPLICATE_EMAIL : ErrorCode.DUPLICATE_USERNAME;

        return ResponseEntity
                .status(errorCode.getCode())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
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
