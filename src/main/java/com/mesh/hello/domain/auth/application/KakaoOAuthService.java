package com.mesh.hello.domain.auth.application;

import com.mesh.hello.domain.auth.dto.KakaoTokenResDTO;
import com.mesh.hello.domain.auth.dto.KakaoUserInfoResDTO;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.enums.Provider;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import com.mesh.hello.global.config.KakaoOAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * 카카오 OAuth 2.0 연동 서비스.
 *
 * <p>컨트롤러는 이 서비스를 아래 순서로 호출한다:
 * <ol>
 *   <li>{@link #buildAuthorizeUrl(String)} — 카카오 인가 페이지로 리다이렉트할 URL 생성</li>
 *   <li>{@link #requestToken(String)} — 인가 코드로 액세스 토큰 교환</li>
 *   <li>{@link #requestUserInfo(String)} — 액세스 토큰으로 카카오 회원 정보 조회</li>
 *   <li>{@link #loginOrSignup(KakaoUserInfoResDTO)} — 조회한 회원 정보로 자체 DB 유저 확보</li>
 * </ol>
 * </p>
 *
 * <p>카카오 HTTP 호출 실패 시 {@link BusinessException}에 {@link ErrorCode#KAKAO_TOKEN_FAILED}
 * 또는 {@link ErrorCode#KAKAO_USER_INFO_FAILED}를 담아 던진다.
 * {@code GlobalExceptionHandler}가 502 응답으로 변환한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    /** 카카오 소셜 유저의 내부 username 접두사. 로컬 회원가입에서는 예약어로 차단된다({@link AuthService#signup}). */
    public static final String USERNAME_PREFIX = "kakao_";

    private final KakaoOAuthProperties kakaoProps;
    private final RestClient restClient;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // -----------------------------------------------------------------------
    // 1. 인가 URL 생성
    // -----------------------------------------------------------------------

    /**
     * 카카오 로그인 인가 URL을 빌드한다.
     *
     * <p>state 파라미터의 생성·저장·검증은 호출 측(컨트롤러) 책임이다.
     * 이 메서드는 전달받은 state를 그대로 URL에 포함한다.</p>
     *
     * @param state CSRF 방지용 상태 토큰 (컨트롤러에서 생성)
     * @return 카카오 인가 페이지 절대 URL
     */
    public String buildAuthorizeUrl(String state) {
        return UriComponentsBuilder
                .fromUriString(kakaoProps.authUri())
                .queryParam("client_id", kakaoProps.clientId())
                .queryParam("redirect_uri", kakaoProps.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .queryParam("scope", "profile_nickname")
                .build()
                .toUriString();
    }

    // -----------------------------------------------------------------------
    // 2. 토큰 교환
    // -----------------------------------------------------------------------

    /**
     * 인가 코드로 카카오 액세스 토큰을 교환한다.
     *
     * <p>카카오 토큰 엔드포인트에 {@code application/x-www-form-urlencoded}로 POST 요청한다.</p>
     *
     * @param code 카카오 인가 서버에서 발급한 인가 코드
     * @return 토큰 응답 DTO
     * @throws BusinessException {@link ErrorCode#KAKAO_TOKEN_FAILED} — HTTP 오류 또는 네트워크 오류
     */
    public KakaoTokenResDTO requestToken(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", kakaoProps.clientId());
        formData.add("client_secret", kakaoProps.clientSecret());
        formData.add("redirect_uri", kakaoProps.redirectUri());
        formData.add("code", code);

        try {
            KakaoTokenResDTO response = restClient.post()
                    .uri(kakaoProps.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        log.warn("카카오 토큰 발급 실패: HTTP {}", res.getStatusCode());
                        throw new BusinessException(ErrorCode.KAKAO_TOKEN_FAILED);
                    })
                    .body(KakaoTokenResDTO.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.KAKAO_TOKEN_FAILED);
            }
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("카카오 토큰 발급 네트워크 오류", e);
            throw new BusinessException(ErrorCode.KAKAO_TOKEN_FAILED);
        }
    }

    // -----------------------------------------------------------------------
    // 3. 유저 정보 조회
    // -----------------------------------------------------------------------

    /**
     * 카카오 액세스 토큰으로 사용자 정보를 조회한다.
     *
     * @param accessToken 카카오 액세스 토큰
     * @return 사용자 정보 DTO (id, nickname)
     * @throws BusinessException {@link ErrorCode#KAKAO_USER_INFO_FAILED} — HTTP 오류 또는 네트워크 오류
     */
    public KakaoUserInfoResDTO requestUserInfo(String accessToken) {
        try {
            KakaoUserInfoResDTO response = restClient.get()
                    .uri(kakaoProps.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        log.warn("카카오 사용자 정보 조회 실패: HTTP {}", res.getStatusCode());
                        throw new BusinessException(ErrorCode.KAKAO_USER_INFO_FAILED);
                    })
                    .body(KakaoUserInfoResDTO.class);

            if (response == null) {
                throw new BusinessException(ErrorCode.KAKAO_USER_INFO_FAILED);
            }
            return response;

        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("카카오 사용자 정보 조회 네트워크 오류", e);
            throw new BusinessException(ErrorCode.KAKAO_USER_INFO_FAILED);
        }
    }

    // -----------------------------------------------------------------------
    // 4. 로그인 or 회원가입
    // -----------------------------------------------------------------------

    /**
     * 카카오 사용자 정보로 자체 DB 유저를 조회하거나 신규 생성한다.
     *
     * <ul>
     *   <li>기존 유저 — {@code (Provider.KAKAO, kakaoId)} 조합으로 검색해 반환</li>
     *   <li>신규 유저 — {@code User.ofSocial()}로 생성, 저장 후 반환.
     *       username = {@code "kakao_" + kakaoId},
     *       password = 랜덤 UUID를 BCrypt 해시(소셜 유저는 비밀번호 로그인 불가),
     *       nickname = 카카오 닉네임 (null 허용)</li>
     * </ul>
     *
     * @param userInfo 카카오 사용자 정보 DTO
     * @return 자체 DB의 {@link User} 엔티티
     */
    @Transactional
    public User loginOrSignup(KakaoUserInfoResDTO userInfo) {
        if (userInfo.getId() == null) {
            log.warn("카카오 사용자 정보에 id가 없음 (null)");
            throw new BusinessException(ErrorCode.KAKAO_USER_INFO_FAILED);
        }
        String providerId = String.valueOf(userInfo.getId());

        return userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId)
                .orElseGet(() -> {
                    String username = USERNAME_PREFIX + providerId;

                    // username은 전역 유니크 컬럼이라, 같은 이름을 가진 LOCAL 계정이 이미 있으면
                    // 저장 시 유니크 제약 위반이 발생한다. 회원가입 단에서 kakao_ 접두사를 예약어로
                    // 막아두었지만(AuthService#signup), 과거 데이터 등 예외 상황을 대비해 방어적으로 확인한다.
                    if (userRepository.existsByUsername(username)) {
                        log.warn("카카오 신규 유저 생성 시 username 충돌: username={}", username);
                        throw new BusinessException(ErrorCode.KAKAO_USERNAME_CONFLICT);
                    }

                    // 소셜 유저는 비밀번호 로그인 불가 → 추측 불가능한 랜덤 BCrypt 해시 저장
                    String password = passwordEncoder.encode(UUID.randomUUID().toString());

                    User newUser = User.ofSocial(
                            username,
                            password,
                            userInfo.getNickname(),
                            Provider.KAKAO,
                            providerId
                    );
                    return userRepository.save(newUser);
                });
    }
}
