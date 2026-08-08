package com.mesh.hello.domain.auth.controller;

import com.mesh.hello.domain.auth.application.AuthService;
import com.mesh.hello.domain.auth.application.KakaoOAuthService;
import com.mesh.hello.domain.auth.dto.KakaoUserInfoResDTO;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * 카카오 OAuth 2.0 로그인 진입/콜백 엔드포인트.
 *
 * <p>{@code @RestController}이므로 {@code WebMvcConfig}의 {@code /api/v1} 프리픽스가 붙는다.
 * 최종 경로는 {@code /api/v1/oauth/kakao/login}, {@code /api/v1/oauth/kakao/callback}이며
 * {@code SecurityConfig}에서 {@code /api/v1/oauth/kakao/**} permitAll로 열려 있다.</p>
 *
 * <p>흐름:
 * <ol>
 *   <li>FE가 {@code /login}으로 이동 → state 생성·세션 저장 → 카카오 인가 페이지로 302</li>
 *   <li>카카오가 {@code /callback?code=&state=}로 리다이렉트 → state 검증 →
 *       토큰 교환 → 유저 정보 조회 → 로그인/가입 → 세션 발급</li>
 *   <li>성공/실패에 따라 프론트엔드 페이지로 302</li>
 * </ol>
 * </p>
 *
 * <p>JSESSIONID 쿠키는 {@code /login}에서 HttpSession을 만들 때 심기고,
 * 콜백의 {@code createSession()} → {@code SecurityContextRepository.saveContext()}가
 * 같은 세션에 SecurityContext를 저장한다. 따라서 쿠키를 직접 다룰 필요는 없다.
 * (콜백은 카카오에서 오는 top-level GET 이동이므로 SameSite=Lax 기본값에서도 쿠키가 함께 전송된다.)</p>
 */
@Slf4j
@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/oauth/kakao")
public class KakaoOAuthController {

    /** CSRF 방지용 state를 보관하는 HttpSession 속성명. */
    private static final String STATE_ATTRIBUTE = "oauth_state";

    /** 로그인 진입 시점의 익명 sessionId를 콜백까지 왕복시키는 HttpSession 속성명. */
    private static final String PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE = "pending_anonymous_session_id";

    private final KakaoOAuthService kakaoOAuthService;
    private final AuthService authService;
    private final String frontendUrl;

    public KakaoOAuthController(KakaoOAuthService kakaoOAuthService,
                                AuthService authService,
                                @Value("${frontend-url}") String frontendUrl) {
        this.kakaoOAuthService = kakaoOAuthService;
        this.authService = authService;
        this.frontendUrl = frontendUrl;
    }

    // -----------------------------------------------------------------------
    // 1. 로그인 진입 — 카카오 인가 페이지로 리다이렉트
    // -----------------------------------------------------------------------

    /**
     * 카카오 인가 페이지로 302 리다이렉트한다.
     *
     * <p>state를 생성해 HttpSession에 저장하고, 익명 sessionId({@code sessionId} 헤더 또는
     * HttpSession 속성)를 콜백에서 쓸 수 있도록 같은 세션에 보관한다. 익명 세션이 없으면 보관하지 않는다.</p>
     */
    @Operation(summary = "카카오 로그인 진입",
            description = "state를 발급해 세션에 저장하고 카카오 인가 페이지로 302 리다이렉트합니다. "
                    + "요청 시 sessionId 헤더(익명 세션 ID)를 함께 보내면 콜백에서 계정에 바인딩됩니다.")
    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(true);

        String state = UUID.randomUUID().toString();
        session.setAttribute(STATE_ATTRIBUTE, state);

        // 콜백 요청에는 sessionId 헤더가 없다(카카오가 보낸 리다이렉트) → 지금 읽어서 세션에 왕복 보관
        authService.resolveAnonymousSessionId(httpRequest)
                .ifPresentOrElse(
                        anonymousSessionId -> session.setAttribute(
                                PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE, anonymousSessionId),
                        () -> session.removeAttribute(PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE));

        return redirect(kakaoOAuthService.buildAuthorizeUrl(state));
    }

    // -----------------------------------------------------------------------
    // 2. 콜백 — 토큰 교환 → 유저 정보 → 로그인/가입 → 세션 발급
    // -----------------------------------------------------------------------

    /**
     * 카카오 인가 서버의 콜백. 성공 시 {@code ${frontend-url}/oauth/success},
     * 실패 시 {@code ${frontend-url}/oauth/fail?reason=...}로 302 리다이렉트한다.
     *
     * <p>브라우저 이동 중인 요청이므로 예외를 그대로 던지지 않고(JSON 에러가 사용자에게 노출되므로)
     * 실패 사유만 {@code reason}으로 축약해 전달한다. 민감정보는 담지 않는다.</p>
     *
     * @param code  카카오 인가 코드 (실패 리다이렉트를 위해 required=false)
     * @param state 로그인 진입 시 발급한 state (실패 리다이렉트를 위해 required=false)
     */
    @Operation(summary = "카카오 로그인 콜백",
            description = "state 검증 후 토큰 교환·유저 정보 조회·로그인/가입·세션 발급을 수행하고 "
                    + "프론트엔드 성공/실패 페이지로 302 리다이렉트합니다.")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        try {
            validateState(httpRequest, state);

            if (code == null || code.isBlank()) {
                // 사용자가 동의를 거부하면 code 없이 error 파라미터만 돌아온다
                log.warn("카카오 콜백에 인가 코드가 없습니다.");
                throw new BusinessException(ErrorCode.KAKAO_TOKEN_FAILED);
            }

            String accessToken = kakaoOAuthService.requestToken(code).accessToken();
            KakaoUserInfoResDTO userInfo = kakaoOAuthService.requestUserInfo(accessToken);
            User user = kakaoOAuthService.loginOrSignup(userInfo);

            // 로그인 진입 시 보관해둔 익명 sessionId를 꺼내 세션 발급 + 바인딩에 사용 (사용 후 제거)
            String anonymousSessionId = consumePendingAnonymousSessionId(httpRequest);
            authService.createSession(user, httpRequest, httpResponse, anonymousSessionId);

            return redirect(frontendUrl + "/oauth/success");

        } catch (BusinessException e) {
            return redirect(failUrl(resolveFailReason(e.getErrorCode())));
        } catch (Exception e) {
            log.error("카카오 로그인 콜백 처리 실패", e);
            return redirect(failUrl("server_error"));
        }
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------

    /**
     * state를 검증한다. 세션에 저장된 값과 일치하지 않거나 없으면 CSRF로 판단해 예외를 던진다.
     *
     * <p>검증 결과와 무관하게 세션의 state 속성은 제거한다(1회용).</p>
     *
     * @throws BusinessException {@link ErrorCode#OAUTH_STATE_MISMATCH}
     */
    private void validateState(HttpServletRequest httpRequest, String state) {
        HttpSession session = httpRequest.getSession(false);
        Object saved = (session == null) ? null : session.getAttribute(STATE_ATTRIBUTE);
        if (session != null) {
            session.removeAttribute(STATE_ATTRIBUTE);
        }

        if (state == null || state.isBlank() || !(saved instanceof String savedState) || !savedState.equals(state)) {
            log.warn("카카오 OAuth state 불일치 — 세션 저장값 존재={}, 파라미터 존재={}",
                    saved != null, state != null && !state.isBlank());
            throw new BusinessException(ErrorCode.OAUTH_STATE_MISMATCH);
        }
    }

    /** 로그인 진입 시 보관한 익명 sessionId를 꺼내고 세션에서 제거한다. 없으면 null. */
    private String consumePendingAnonymousSessionId(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return null;
        }
        Object attribute = session.getAttribute(PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE);
        session.removeAttribute(PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE);

        return (attribute instanceof String sessionId && !sessionId.isBlank()) ? sessionId : null;
    }

    /** 에러 코드를 프론트에 노출할 실패 사유로 축약한다(민감정보 제외). */
    private String resolveFailReason(ErrorCode errorCode) {
        return switch (errorCode) {
            case OAUTH_STATE_MISMATCH -> "state_mismatch";
            case KAKAO_TOKEN_FAILED -> "token_failed";
            case KAKAO_USER_INFO_FAILED -> "user_info_failed";
            default -> "server_error";
        };
    }

    private String failUrl(String reason) {
        return UriComponentsBuilder.fromUriString(frontendUrl + "/oauth/fail")
                .queryParam("reason", reason)
                .build()
                .toUriString();
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }
}
