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
    INVALID_REQUEST_BODY(400, "요청 본문을 읽을 수 없습니다."),
    SUMMARY_PENDING(202, "요약을 생성하고 있습니다."),
    SUMMARY_FAILED(500, "AI 요약 생성에 실패했습니다."),

    // 인증/로그인
    LOGIN_FAILED(401, "아이디 또는 비밀번호가 일치하지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    DUPLICATE_USERNAME(409, "이미 사용 중인 사용자명입니다."),
    INVALID_USERNAME_FORMAT(400, "사용자명은 영문/숫자/언더스코어 3~20자여야 합니다."),

    // 비밀번호 변경
    // ⚠️ 삽입 위치 주의: GlobalExceptionHandler.handleValidation은 여러 필드가 동시에 검증
    //    실패했을 때 ErrorCode.ordinal()이 가장 작은 상수를 대표로 골라 응답한다.
    //    PASSWORD_MISMATCH가 INVALID_PASSWORD_FORMAT보다 앞에 있어야 "현재 비밀번호" 오류가
    //    "새 비밀번호 형식" 오류보다 우선 노출된다.
    //    ErrorCode는 @Enumerated 등으로 어디에도 영속화되지 않으므로 상수 중간 삽입은 안전하다.
    PASSWORD_MISMATCH(400, "현재 비밀번호가 일치하지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(400, "새 비밀번호가 현재 비밀번호와 동일합니다."),
    SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED(400, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),

    INVALID_PASSWORD_FORMAT(400, "비밀번호는 영문·숫자를 포함한 8~20자여야 합니다."),
    INVALID_EMAIL_FORMAT(400, "이메일 형식이 올바르지 않습니다."),
    INVALID_NICKNAME_FORMAT(400, "닉네임은 2~20자여야 합니다."),
    PASSWORD_CONFIRM_MISMATCH(400, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    PRIVACY_AGREEMENT_REQUIRED(400, "개인정보 수집 및 이용에 동의해야 합니다."),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),
    RESERVED_USERNAME_PREFIX(400, "예약어로 사용할 수 없는 사용자명 형식입니다."),
    KAKAO_USERNAME_CONFLICT(409, "카카오 로그인에 실패했습니다. 관리자에게 문의해주세요."),

    // 탈퇴
    ALREADY_WITHDRAWN(400, "이미 탈퇴한 계정입니다."),

    // 카카오 OAuth
    OAUTH_STATE_MISMATCH(401, "OAuth state 값이 유효하지 않습니다."),
    KAKAO_TOKEN_FAILED(502, "카카오 토큰 발급에 실패했습니다."),
    KAKAO_USER_INFO_FAILED(502, "카카오 사용자 정보 조회에 실패했습니다."),

    // 정책(약관/개인정보처리방침)
    INVALID_POLICY_TYPE(400, "존재하지 않는 정책 유형입니다."),
    POLICY_NOT_FOUND(404, "등록된 정책 내용이 없습니다."),

    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다."),
    NO_HELPER_SEQUENCE(500, "HELPER_SEQUENCE가 존재하지 않습니다."),
    NO_HELPEE_SEQUENCE(500, "HELPEE_SEQUENCE가 존재하지 않습니다.")
    ;

    private final int code;
    private final String message;
}
