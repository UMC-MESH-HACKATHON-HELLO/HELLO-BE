package com.mesh.hello.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 토큰 발급 API({@code /oauth/token}) 응답 매핑.
 *
 * <p>카카오는 snake_case JSON을 반환하므로 {@link JsonProperty}로 각 필드를 명시한다.
 * 역직렬화(읽기) 전용이므로 record로 선언한다.</p>
 *
 * @param accessToken           액세스 토큰
 * @param tokenType             토큰 타입 (항상 "bearer")
 * @param refreshToken          리프레시 토큰
 * @param expiresIn             액세스 토큰 유효 시간(초)
 * @param scope                 부여된 동의 항목 목록 (공백 구분)
 * @param refreshTokenExpiresIn 리프레시 토큰 유효 시간(초)
 */
public record KakaoTokenResDTO(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("refresh_token")
        String refreshToken,

        @JsonProperty("expires_in")
        int expiresIn,

        @JsonProperty("scope")
        String scope,

        @JsonProperty("refresh_token_expires_in")
        int refreshTokenExpiresIn
) {}
