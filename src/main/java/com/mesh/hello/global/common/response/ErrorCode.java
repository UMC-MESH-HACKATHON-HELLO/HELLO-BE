package com.mesh.hello.global.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 응답 코드 정의. (명세서의 에러 분기와 1:1)
 *
 * <p>상수명 = 명세서 식별자, {@code code} = HTTP 상태 기준 숫자 코드.
 * {@link com.mesh.hello.global.common.exception.GlobalExceptionHandler}가 이 값으로
 * {@code {code, message, result:null}} 형식의 공통 응답을 만든다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_SESSION(401, "세션이 없거나 만료되었습니다."),
    ROLE_NOT_ALLOWED(403, "허용되지 않은 역할 호출입니다."),
    ALREADY_IN_CALL(409, "이미 통화 중입니다."),
    NOT_FOUND(404, "대상 룸 또는 요청을 찾을 수 없습니다."),
    SUMMARY_PENDING(202, "요약을 생성하고 있습니다."),
    SUMMARY_FAILED(500, "AI 요약 생성에 실패했습니다."),

    // 인증/로그인
    LOGIN_FAILED(401, "아이디 또는 비밀번호가 일치하지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    DUPLICATE_USERNAME(409, "이미 사용 중인 사용자명입니다."),

    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int code;
    private final String message;
}
