package com.mesh.hello.domain.auth.dto;

/**
 * 회원가입 성공 응답. password는 절대 노출하지 않는다.
 */
public record SignupResponse(Long userId, String username, String nickname) {
}
