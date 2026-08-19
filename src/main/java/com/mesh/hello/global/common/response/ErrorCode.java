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
    INVALID_PAGING(400, "page/size 파라미터가 올바르지 않습니다."),
    SUMMARY_PENDING(202, "요약을 생성하고 있습니다."),
    SUMMARY_FAILED(500, "AI 요약 생성에 실패했습니다."),

    // 인증/로그인
    LOGIN_FAILED(401, "아이디 또는 비밀번호가 일치하지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    DUPLICATE_USERNAME(409, "이미 사용 중인 사용자명입니다."),
    INVALID_USERNAME_FORMAT(400, "사용자명은 영문/숫자/언더스코어 3~20자여야 합니다."),
    INVALID_PASSWORD_FORMAT(400, "비밀번호는 영문·숫자·특수문자를 포함한 8~20자여야 합니다."),
    INVALID_EMAIL_FORMAT(400, "이메일 형식이 올바르지 않습니다."),
    INVALID_NICKNAME_FORMAT(400, "닉네임은 2~10자여야 합니다."),
    PASSWORD_CONFIRM_MISMATCH(400, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    PRIVACY_AGREEMENT_REQUIRED(400, "개인정보 수집 및 이용에 동의해야 합니다."),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),
    RESERVED_USERNAME_PREFIX(400, "카카오 로그인 전용으로 예약된 사용자명 형식입니다."),
    KAKAO_USERNAME_CONFLICT(409, "카카오 로그인에 실패했습니다. 관리자에게 문의해주세요."),

    // 탈퇴
    ALREADY_WITHDRAWN(400, "이미 탈퇴한 계정입니다."),
    WITHDRAW_BLOCKED_BY_CALL(409, "진행 중인 통화가 있어 탈퇴할 수 없습니다."),

    // 카카오 OAuth
    OAUTH_STATE_MISMATCH(401, "OAuth state 값이 유효하지 않습니다."),
    KAKAO_TOKEN_FAILED(502, "카카오 토큰 발급에 실패했습니다."),
    KAKAO_USER_INFO_FAILED(502, "카카오 사용자 정보 조회에 실패했습니다."),

    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다.");

    private final int code;
    private final String message;
}
