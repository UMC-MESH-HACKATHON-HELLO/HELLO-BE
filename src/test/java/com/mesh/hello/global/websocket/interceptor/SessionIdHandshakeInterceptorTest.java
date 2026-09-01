package com.mesh.hello.global.websocket.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mesh.hello.global.websocket.support.WebSocketConst;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * {@link SessionIdHandshakeInterceptor} 단위 테스트.
 *
 * <p>sessionId는 오직 실제 {@code HttpSession}의 id로만 확정되고, 클라이언트가 채운
 * 쿼리파라미터/헤더는 더 이상 신뢰하지 않는지 검증한다.</p>
 */
class SessionIdHandshakeInterceptorTest {

    private final SessionIdHandshakeInterceptor interceptor = new SessionIdHandshakeInterceptor();

    @Test
    @DisplayName("HttpSession이 있으면 그 id를 sessionId로 사용한다")
    void usesHttpSessionId() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        servletRequest.setSession(session);

        Map<String, Object> attributes = beforeHandshake(servletRequest);

        assertThat(attributes.get(WebSocketConst.SESSION_ID_ATTRIBUTE)).isEqualTo(session.getId());
    }

    @Test
    @DisplayName("클라이언트가 보낸 쿼리파라미터 sessionId는 무시한다")
    void ignoresClientSuppliedQueryParam() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setQueryString("sessionId=victim-session-id");
        servletRequest.setParameter("sessionId", "victim-session-id");
        MockHttpSession session = new MockHttpSession();
        servletRequest.setSession(session);

        Map<String, Object> attributes = beforeHandshake(servletRequest);

        assertThat(attributes.get(WebSocketConst.SESSION_ID_ATTRIBUTE))
            .isEqualTo(session.getId())
            .isNotEqualTo("victim-session-id");
    }

    @Test
    @DisplayName("클라이언트가 보낸 sessionId 헤더는 무시한다")
    void ignoresClientSuppliedHeader() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("sessionId", "victim-session-id");
        MockHttpSession session = new MockHttpSession();
        servletRequest.setSession(session);

        Map<String, Object> attributes = beforeHandshake(servletRequest);

        assertThat(attributes.get(WebSocketConst.SESSION_ID_ATTRIBUTE))
            .isEqualTo(session.getId())
            .isNotEqualTo("victim-session-id");
    }

    @Test
    @DisplayName("HttpSession이 없으면 1회성 UUID를 발급한다")
    void generatesUuidWhenNoSession() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        Map<String, Object> attributes = beforeHandshake(servletRequest);

        String sessionId = (String) attributes.get(WebSocketConst.SESSION_ID_ATTRIBUTE);
        assertThat(sessionId).isNotBlank();
        assertThat(sessionId).hasSize(36); // UUID 문자열 길이
    }

    private Map<String, Object> beforeHandshake(MockHttpServletRequest servletRequest) {
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        Map<String, Object> attributes = new HashMap<>();

        interceptor.beforeHandshake(request, response, null, attributes);

        return attributes;
    }
}