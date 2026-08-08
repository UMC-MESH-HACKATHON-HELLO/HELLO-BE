package com.mesh.hello.domain.auth.controller;

import com.mesh.hello.domain.auth.application.AuthService;
import com.mesh.hello.domain.auth.application.KakaoOAuthService;
import com.mesh.hello.domain.auth.dto.KakaoTokenResDTO;
import com.mesh.hello.domain.auth.dto.KakaoUserInfoResDTO;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link KakaoOAuthController} 콜백의 state 검증(CSRF 방어) 단위 테스트.
 *
 * <p>MockMvc 대신 {@code MockHttpServletRequest}/{@code MockHttpSession}으로 컨트롤러를 직접 호출한다
 * (기존 테스트가 모두 순수 Mockito 단위 테스트 컨벤션이라 이에 맞췄다).
 * 카카오 HTTP 호출({@code requestToken}, {@code requestUserInfo})과 세션 발급({@code createSession})은
 * 목킹해 state 검증 흐름에만 집중한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class KakaoOAuthControllerTest {

    private static final String FRONTEND_URL = "https://hello.example.com";
    private static final String STATE_ATTRIBUTE = "oauth_state";
    private static final String PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE = "pending_anonymous_session_id";

    private static final String SUCCESS_URL = FRONTEND_URL + "/oauth/success";
    private static final String STATE_MISMATCH_URL = FRONTEND_URL + "/oauth/fail?reason=state_mismatch";

    @Mock
    private KakaoOAuthService kakaoOAuthService;

    @Mock
    private AuthService authService;

    private KakaoOAuthController controller;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        controller = new KakaoOAuthController(kakaoOAuthService, authService, FRONTEND_URL);
        request = new MockHttpServletRequest("GET", "/api/v1/oauth/kakao/callback");
        response = new MockHttpServletResponse();
    }

    // -----------------------------------------------------------------------
    // 픽스처
    // -----------------------------------------------------------------------

    /** 세션에 state를 심어둔 상태(= /login을 거친 상태)를 만든다. */
    private MockHttpSession givenSessionWithState(String state) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(STATE_ATTRIBUTE, state);
        request.setSession(session);
        return session;
    }

    /** 카카오 API 3단계(토큰 → 유저 정보 → 로그인/가입)를 모두 성공으로 목킹한다. */
    private void givenKakaoApiSucceeds() {
        KakaoTokenResDTO token = new KakaoTokenResDTO(
                "test-access-token", "bearer", "test-refresh-token", 3600, "profile_nickname", 5184000);
        KakaoUserInfoResDTO userInfo = new KakaoUserInfoResDTO() {
            @Override
            public Long getId() {
                return 1234567890L;
            }

            @Override
            public String getNickname() {
                return "카카오닉네임";
            }
        };
        User user = User.ofSocial("kakao_1234567890", "hashed", "카카오닉네임", Provider.KAKAO, "1234567890");

        given(kakaoOAuthService.requestToken(anyString())).willReturn(token);
        given(kakaoOAuthService.requestUserInfo("test-access-token")).willReturn(userInfo);
        given(kakaoOAuthService.loginOrSignup(userInfo)).willReturn(user);
    }

    private String locationOf(ResponseEntity<Void> result) {
        assertThat(result.getHeaders().getLocation()).isNotNull();
        return result.getHeaders().getLocation().toString();
    }

    // -----------------------------------------------------------------------
    // 1. 정상 흐름
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("state 일치 - 콜백 정상 진행")
    class StateMatches {

        @Test
        @DisplayName("세션 state와 파라미터 state가 같으면 성공 페이지로 302 리다이렉트한다")
        void redirectsToSuccess() {
            // given
            givenSessionWithState("valid-state");
            givenKakaoApiSucceeds();

            // when
            ResponseEntity<Void> result = controller.callback("auth-code", "valid-state", request, response);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(locationOf(result)).isEqualTo(SUCCESS_URL);
        }

        @Test
        @DisplayName("카카오 API 3단계를 순서대로 호출하고 세션을 발급한다")
        void callsKakaoApiAndCreatesSession() {
            // given
            givenSessionWithState("valid-state");
            givenKakaoApiSucceeds();

            // when
            controller.callback("auth-code", "valid-state", request, response);

            // then
            verify(kakaoOAuthService).requestToken("auth-code");
            verify(kakaoOAuthService).requestUserInfo("test-access-token");
            verify(kakaoOAuthService).loginOrSignup(any(KakaoUserInfoResDTO.class));
            verify(authService).createSession(any(User.class), eq(request), eq(response), any());
        }

        @Test
        @DisplayName("로그인 진입 시 보관한 익명 sessionId를 createSession에 넘기고 세션에서 제거한다")
        void passesAndClearsPendingAnonymousSessionId() {
            // given
            MockHttpSession session = givenSessionWithState("valid-state");
            session.setAttribute(PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE, "anon-session-123");
            givenKakaoApiSucceeds();

            // when
            controller.callback("auth-code", "valid-state", request, response);

            // then
            verify(authService).createSession(any(User.class), eq(request), eq(response), eq("anon-session-123"));
            assertThat(session.getAttribute(PENDING_ANONYMOUS_SESSION_ID_ATTRIBUTE)).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // 2~4. state 검증 실패
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("state 검증 실패 - CSRF 방어")
    class StateValidationFails {

        @Test
        @DisplayName("state가 세션 저장값과 다르면 reason=state_mismatch로 실패 리다이렉트한다")
        void mismatchedState() {
            // given
            givenSessionWithState("session-state");

            // when
            ResponseEntity<Void> result = controller.callback("auth-code", "attacker-state", request, response);

            // then
            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(locationOf(result)).isEqualTo(STATE_MISMATCH_URL);
        }

        @Test
        @DisplayName("state 파라미터가 null이면 실패 리다이렉트한다")
        void nullStateParam() {
            // given
            givenSessionWithState("session-state");

            // when
            ResponseEntity<Void> result = controller.callback("auth-code", null, request, response);

            // then
            assertThat(locationOf(result)).isEqualTo(STATE_MISMATCH_URL);
        }

        @Test
        @DisplayName("state 파라미터가 공백이면 실패 리다이렉트한다")
        void blankStateParam() {
            // given
            givenSessionWithState("session-state");

            // when
            ResponseEntity<Void> result = controller.callback("auth-code", "   ", request, response);

            // then
            assertThat(locationOf(result)).isEqualTo(STATE_MISMATCH_URL);
        }

        @Test
        @DisplayName("세션에 state가 없으면(만료) 실패 리다이렉트한다")
        void noStateInSession() {
            // given — 세션은 있지만 oauth_state 속성이 없다
            request.setSession(new MockHttpSession());

            // when
            ResponseEntity<Void> result = controller.callback("auth-code", "some-state", request, response);

            // then
            assertThat(locationOf(result)).isEqualTo(STATE_MISMATCH_URL);
        }

        @Test
        @DisplayName("세션 자체가 없으면(콜백 URL 직접 진입) 실패 리다이렉트한다")
        void noSessionAtAll() {
            // given — request.setSession()을 하지 않아 getSession(false)가 null

            // when
            ResponseEntity<Void> result = controller.callback("auth-code", "some-state", request, response);

            // then
            assertThat(locationOf(result)).isEqualTo(STATE_MISMATCH_URL);
        }

        @Test
        @DisplayName("state 검증에 실패하면 카카오 API를 호출하지 않고 세션도 발급하지 않는다")
        void doesNotCallKakaoApiNorCreateSession() {
            // given
            givenSessionWithState("session-state");

            // when
            controller.callback("auth-code", "attacker-state", request, response);

            // then
            verifyNoInteractions(kakaoOAuthService);
            verify(authService, never()).createSession(any(), any(), any(), any());
            verify(authService, never()).createSession(any(), any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // 5. state 재사용 차단
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("state 1회용 - 검증 후 세션에서 제거")
    class StateIsSingleUse {

        @Test
        @DisplayName("검증 성공 후 세션의 oauth_state 속성이 제거된다")
        void removedAfterSuccess() {
            // given
            MockHttpSession session = givenSessionWithState("valid-state");
            givenKakaoApiSucceeds();

            // when
            controller.callback("auth-code", "valid-state", request, response);

            // then
            assertThat(session.getAttribute(STATE_ATTRIBUTE)).isNull();
        }

        @Test
        @DisplayName("검증 실패 후에도 세션의 oauth_state 속성이 제거된다")
        void removedAfterFailure() {
            // given
            MockHttpSession session = givenSessionWithState("session-state");

            // when
            controller.callback("auth-code", "attacker-state", request, response);

            // then
            assertThat(session.getAttribute(STATE_ATTRIBUTE)).isNull();
        }

        @Test
        @DisplayName("같은 state로 두 번째 콜백을 보내면 실패 리다이렉트한다(재사용 차단)")
        void sameStateRejectedOnReplay() {
            // given — 첫 콜백은 성공
            givenSessionWithState("valid-state");
            givenKakaoApiSucceeds();
            ResponseEntity<Void> first = controller.callback("auth-code", "valid-state", request, response);
            assertThat(locationOf(first)).isEqualTo(SUCCESS_URL);

            // when — 같은 세션·같은 state로 재시도
            ResponseEntity<Void> replay = controller.callback("auth-code", "valid-state", request, response);

            // then
            assertThat(locationOf(replay)).isEqualTo(STATE_MISMATCH_URL);
            // 두 번째 콜백은 토큰 교환까지 가지 못한다 → 총 호출 1회
            verify(kakaoOAuthService, times(1)).requestToken("auth-code");
        }
    }
}
