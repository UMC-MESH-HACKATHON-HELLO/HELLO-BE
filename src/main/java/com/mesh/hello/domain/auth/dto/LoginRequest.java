package com.mesh.hello.domain.auth.dto;

/**
 * 로그인 요청. 프론트에서 평문 username/password를 받는다(HTTPS 전제).
 */
public record LoginRequest(String username, String password) {
}
