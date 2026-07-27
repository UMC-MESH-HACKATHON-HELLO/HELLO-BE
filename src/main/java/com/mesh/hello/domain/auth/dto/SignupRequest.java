package com.mesh.hello.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 모든 필드 필수이며 길이 제한을 둔다(검증 실패 시 400).
 */
public record SignupRequest(

        @NotBlank(message = "username은 필수입니다.")
        @Size(min = 4, max = 20, message = "username은 4~20자여야 합니다.")
        String username,

        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, message = "password는 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "nickname은 필수입니다.")
        @Size(min = 2, max = 10, message = "nickname은 2~10자여야 합니다.")
        String nickname
) {
}
