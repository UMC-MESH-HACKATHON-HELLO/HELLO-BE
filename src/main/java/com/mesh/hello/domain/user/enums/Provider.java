package com.mesh.hello.domain.user.enums;

/**
 * 로그인 제공자 구분.
 *
 * <ul>
 *   <li>{@code LOCAL} - username/password 기반 자체 회원가입</li>
 *   <li>{@code KAKAO} - 카카오 OAuth 소셜 로그인</li>
 * </ul>
 */
public enum Provider {
    LOCAL,
    KAKAO
}
