package com.mesh.hello.domain.auth.dto;

/**
 * 로그인 성공 응답. 토큰이 아니라 도우미 정보(세션 인증이므로 인증은 JSESSIONID 쿠키로 유지).
 */
public record LoginResponse(Long userId, String nickname, long points) {
}
