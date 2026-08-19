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
     * <p>예외 원문은 응답에 노출하지 않는다. 제약 이름으로만 중복 종류를 판단한다.</p>
     *
     * <ul>
     *   <li>메시지에 {@code uq_users_email} 포함 → 이메일 중복</li>
     *   <li>메시지에 {@code uq_users_username} 포함 → 아이디 중복</li>
     *   <li>그 외(FK, NOT NULL, {@code uq_users_provider_provider_id} 등) →
     *       예외 원문을 {@code log.error}로 기록하고 500으로 폴백.
     *       응답 본문에는 원문을 노출하지 않는다.</li>
     * </ul>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        String msgLower = msg != null ? msg.toLowerCase() : "";

        if (msgLower.contains("uq_users_email")) {
            log.warn("이메일 중복 위반: {}", msg);
            return ResponseEntity
                    .status(ErrorCode.DUPLICATE_EMAIL.getCode())
                    .body(ApiResponse.error(ErrorCode.DUPLICATE_EMAIL.getCode(),
                            ErrorCode.DUPLICATE_EMAIL.getMessage()));
        }

        if (msgLower.contains("uq_users_username")) {
            log.warn("사용자명 중복 위반: {}", msg);
            return ResponseEntity
                    .status(ErrorCode.DUPLICATE_USERNAME.getCode())
                    .body(ApiResponse.error(ErrorCode.DUPLICATE_USERNAME.getCode(),
                            ErrorCode.DUPLICATE_USERNAME.getMessage()));
        }

        // 예상 외 제약 위반 — 원문을 로그에만 남기고 500으로 폴백
        log.error("예상하지 못한 데이터 무결성 위반: {}", msg);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getCode())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()));
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
