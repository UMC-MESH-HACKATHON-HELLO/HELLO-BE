package com.mesh.hello.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 변경 요청 DTO.
 *
 * <p>필드는 {@code currentPassword}, {@code newPassword} 두 개뿐이다.
 * 새 비밀번호 확인란은 프론트엔드가 담당한다.</p>
 *
 * <p>각 필드의 {@code message} 속성에는 {@link com.mesh.hello.global.common.response.ErrorCode}
 * 상수 이름을 문자열로 지정한다. {@link com.mesh.hello.global.common.exception.GlobalExceptionHandler}가
 * {@code ErrorCode.valueOf(message)}로 변환해 공통 응답을 만든다.</p>
 *
 * <p>{@code @JsonAutoDetect}와 명시적 기본 생성자는 Jackson 3 역직렬화에 필수다.
 * 이게 없으면 요청 본문이 필드에 바인딩되지 않는다(커밋 971b94b: 회원가입이 같은 이유로 깨졌다).</p>
 *
 * <p>위치가 {@code auth/dto}가 아니라 {@code user/dto}인 이유: 이 DTO를 쓰는 컨트롤러
 * ({@code user/api})와 서비스({@code user/application})가 모두 user 도메인이다.
 * auth/dto에 두면 user → auth 방향의 불필요한 도메인 의존이 생긴다.</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ChangePasswordRequest {

    /**
     * 현재 비밀번호.
     *
     * <p>⚠️ {@code @Pattern}(형식 검증)을 붙이지 않는다. 비밀번호 정책이 강화되기 이전에
     * 가입한 계정은 현재 정책(영문·숫자 포함 8~20자)을 만족하지 않을 수 있는데, 여기에
     * 형식 검증을 붙이면 그런 계정은 비밀번호를 바꿀 방법 자체가 사라진다.
     * 현재 비밀번호의 정합성은 오직 {@code passwordEncoder.matches()}로만 판단한다.</p>
     */
    @Schema(
            description = "현재 비밀번호. 형식 검증 없이 저장된 해시와 일치하는지만 확인합니다.",
            example = "oldPw1234",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "PASSWORD_MISMATCH")
    private String currentPassword;

    /** 새 비밀번호. 영문+숫자를 각각 1자 이상 포함한 8~20자. 특수문자는 선택. */
    @Schema(
            description = "새 비밀번호. 8~20자이며 영문자와 숫자를 각각 1자 이상 포함해야 합니다. "
                    + "특수문자는 넣어도 되고 안 넣어도 됩니다.",
            example = "newPw5678",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "INVALID_PASSWORD_FORMAT")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9]).{8,20}$", message = "INVALID_PASSWORD_FORMAT")
    private String newPassword;

    // ── 생성자 ────────────────────────────────────────────────────────────────

    /** Jackson 역직렬화용 기본 생성자. */
    public ChangePasswordRequest() {}

    /** 테스트용 전체 인수 생성자. */
    public ChangePasswordRequest(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    // ── 접근자 ────────────────────────────────────────────────────────────────

    public String currentPassword() { return currentPassword; }
    public String newPassword() { return newPassword; }
}
