package com.mesh.hello.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yaml의 {@code kakao.*} 블록을 바인딩하는 설정 프로퍼티.
 *
 * <p>등록 방법: {@link com.mesh.hello.HelloApplication}에
 * {@code @EnableConfigurationProperties(KakaoOAuthProperties.class)}를 선언한다.</p>
 *
 * <p>application.yaml 매핑 예시:
 * <pre>{@code
 * kakao:
 *   client-id: ${KAKAO_CLIENT_ID}
 *   client-secret: ${KAKAO_CLIENT_SECRET}
 *   redirect-uri: ${KAKAO_REDIRECT_URI}
 *   auth-uri: https://kauth.kakao.com/oauth/authorize
 *   token-uri: https://kauth.kakao.com/oauth/token
 *   user-info-uri: https://kapi.kakao.com/v2/user/me
 * }</pre>
 * </p>
 *
 * @param clientId     카카오 REST API 키
 * @param clientSecret 카카오 Client Secret (보안 강화 설정 시 필수)
 * @param redirectUri  카카오 인가 후 리다이렉트될 서버 콜백 URI
 * @param authUri      카카오 인가 코드 요청 URI (고정값)
 * @param tokenUri     카카오 토큰 발급 URI (고정값)
 * @param userInfoUri  카카오 사용자 정보 조회 URI (고정값)
 */
@ConfigurationProperties(prefix = "kakao")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authUri,
        String tokenUri,
        String userInfoUri
) {}
