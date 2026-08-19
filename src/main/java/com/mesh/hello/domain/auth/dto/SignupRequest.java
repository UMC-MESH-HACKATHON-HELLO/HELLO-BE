package com.mesh.hello.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 DTO.
 *
 * <p>username은 영문/숫자/언더스코어 3~20자여야 하며, {@code kakao_} 접두사는
 * 카카오 소셜 로그인 유저의 내부 username 생성에 사용되는 예약어라 로컬 가입에서는 거부된다.
 * {@code kakao_} 차단은 Bean Validation으로 표현하기 어려우므로
 * {@link com.mesh.hello.domain.auth.application.AuthService#signup}에서 수행한다.</p>
 *
 * <p>각 필드의 {@code message} 속성에는 {@link com.mesh.hello.global.common.response.ErrorCode}
 * 상수 이름을 문자열로 지정한다. {@link com.mesh.hello.global.common.exception.GlobalExceptionHandler}가
 * {@code ErrorCode.valueOf(message)}로 변환해 공통 응답을 만든다.</p>
 *
 * <p>nickname은 선택값이다. null 또는 빈 문자열이 모두 허용되며, 저장 시 null/공백이면
 * username을 닉네임으로 대체한다({@link com.mesh.hello.domain.user.domain.User#createLocal} 참고).</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SignupRequest {

    /** 영문/숫자/언더스코어 3~20자. kakao_ 접두사 차단은 서비스 레이어에서 수행. */
    @NotBlank(message = "INVALID_USERNAME_FORMAT")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", message = "INVALID_USERNAME_FORMAT")
    private String username;

    /** 영문+숫자를 모두 포함한 8~20자. */
    @NotBlank(message = "INVALID_PASSWORD_FORMAT")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$", message = "INVALID_PASSWORD_FORMAT")
    private String password;

    /** 비밀번호 확인. password와 일치 여부는 {@link #isPasswordConfirmValid()}로 검증. */
    @NotBlank(message = "PASSWORD_CONFIRM_MISMATCH")
    private String passwordConfirm;

    /** 이메일 주소. 최대 100자, 이메일 형식. */
    @NotBlank(message = "INVALID_EMAIL_FORMAT")
    @Email(message = "INVALID_EMAIL_FORMAT")
    @Size(max = 100, message = "INVALID_EMAIL_FORMAT")
    private String email;

    /**
     * 닉네임. 선택값.
     * null 또는 빈 문자열("")은 허용한다(프론트가 빈 문자열을 보낼 수 있음).
     * 값이 존재하고 비어있지 않을 때만 2~20자 규칙 적용 — {@link #isNicknameValid()} 참고.
     */
    private String nickname;

    /** 개인정보 수집·이용 동의. true만 허용. */
    @NotNull(message = "PRIVACY_AGREEMENT_REQUIRED")
    @AssertTrue(message = "PRIVACY_AGREEMENT_REQUIRED")
    private Boolean privacyAgreed;

    // ── 생성자 ────────────────────────────────────────────────────────────────

    /** Jackson 역직렬화용 기본 생성자. */
    public SignupRequest() {}

    /** 테스트용 전체 인수 생성자. */
    public SignupRequest(String username, String password, String passwordConfirm,
                         String email, String nickname, Boolean privacyAgreed) {
        this.username = username;
        this.password = password;
        this.passwordConfirm = passwordConfirm;
        this.email = email;
        this.nickname = nickname;
        this.privacyAgreed = privacyAgreed;
    }

    /**
     * 기존 테스트 호환용 생성자 (username, password, nickname 3-인수).
     *
     * <p>기존 {@link com.mesh.hello.domain.auth.application.AuthServiceTest}가
     * {@code new SignupRequest(username, password, nickname)} 형태로 생성한다.
     * 이 생성자를 유지해 컴파일 오류를 막는다.</p>
     */
    public SignupRequest(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }

    // ── 접근자 ────────────────────────────────────────────────────────────────

    public String username() { return username; }
    public String password() { return password; }
    public String passwordConfirm() { return passwordConfirm; }
    public String email() { return email; }
    public String nickname() { return nickname; }
    public Boolean privacyAgreed() { return privacyAgreed; }

    // ── 크로스 필드 검증 ──────────────────────────────────────────────────────

    /**
     * password와 passwordConfirm이 일치하는지 검증한다.
     *
     * <p>password 또는 passwordConfirm이 null/빈값이면 각 필드의 @NotBlank가 먼저 잡으므로
     * 여기서는 일치 여부만 확인한다. null 비교는 false를 반환해 mismatch 에러를 낸다.</p>
     */
    @AssertTrue(message = "PASSWORD_CONFIRM_MISMATCH")
    public boolean isPasswordConfirmValid() {
        if (password == null || passwordConfirm == null) {
            return true; // null은 각 필드의 @NotBlank에서 잡음, 여기서는 통과시켜 중복 에러 방지
        }
        return password.equals(passwordConfirm);
    }

    /**
     * 닉네임 길이 검증. null 또는 빈 문자열("")은 허용한다(선택값).
     * 값이 존재하고 비어있지 않을 때만 2~20자를 검사한다.
     */
    @AssertTrue(message = "INVALID_NICKNAME_FORMAT")
    public boolean isNicknameValid() {
        if (nickname == null || nickname.isEmpty()) {
            return true; // 선택값 — null과 빈 문자열 모두 통과
        }
        return nickname.length() >= 2 && nickname.length() <= 20;
    }
}
