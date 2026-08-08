package com.mesh.hello.domain.auth.dto;

/**
 * 회원가입 요청(최소 버전). nickname은 선택.
 *
 * <p>username은 영문/숫자/언더스코어 3~20자여야 하며, {@code kakao_} 접두사는
 * 카카오 소셜 로그인 유저의 내부 username 생성에 사용되는 예약어라 로컬 가입에서는 거부된다.
 * 실제 검증은 {@link com.mesh.hello.domain.auth.application.AuthService#signup}에서 수행한다.</p>
 */
public record SignupRequest(String username, String password, String nickname) {
}
