package com.mesh.hello.global.websocket.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mesh.hello.global.websocket.support.WebSocketConst;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link AnonymousPrincipalChannelInterceptor} 단위 테스트.
 *
 * <p>sessionId는 핸드셰이크 attributes(HttpSession 기반으로 확정된 값)만 신뢰하고,
 * CONNECT 프레임의 sessionId 네이티브 헤더는 스푸핑 가능하므로 무시하는지 검증한다.</p>
 */
class AnonymousPrincipalChannelInterceptorTest {

    private final AnonymousPrincipalChannelInterceptor interceptor =
        new AnonymousPrincipalChannelInterceptor();

    /**
     * CONNECT 프레임의 sessionId 네이티브 헤더는 클라이언트가 자유롭게 채울 수 있는 값이라
     * 신뢰하지 않는다 — 핸드셰이크 값이 있으면 프레임 헤더는 무시하고 그 값을 사용한다.
     */
    @Test
    @DisplayName("CONNECT 프레임 헤더는 무시하고 핸드셰이크 sessionId를 사용한다")
    void ignoresFrameHeaderAndUsesHandshake() {
        StompHeaderAccessor accessor = connect();
        accessor.setNativeHeader(WebSocketConst.SESSION_ID_ATTRIBUTE, "fromFrame");
        accessor.setSessionAttributes(handshakeAttrs("fromHandshake"));

        interceptor.preSend(message(accessor), null);

        assertThat(nameOf(accessor)).isEqualTo("fromHandshake");
    }

    /**
     * 핸드셰이크에서 넘어온 값을 사용한다.
     */
    @Test
    @DisplayName("핸드셰이크 attributes의 sessionId를 사용한다")
    void usesHandshakeSessionId() {
        StompHeaderAccessor accessor = connect();
        accessor.setSessionAttributes(handshakeAttrs("fromHandshake"));

        interceptor.preSend(message(accessor), null);

        assertThat(nameOf(accessor)).isEqualTo("fromHandshake");
    }

    /**
     * 둘 다 없으면 안전장치로 UUID를 발급해 반드시 Principal이 존재하게 한다.
     */
    @Test
    @DisplayName("프레임/핸드셰이크 모두 없으면 UUID를 발급한다")
    void generatesUuidWhenAbsent() {
        StompHeaderAccessor accessor = connect();

        interceptor.preSend(message(accessor), null);

        assertThat(nameOf(accessor)).isNotBlank();
        assertThat(nameOf(accessor)).hasSize(36); // UUID 문자열 길이
    }

    /**
     * CONNECT가 아닌 프레임에는 Principal을 손대지 않는다.
     */
    @Test
    @DisplayName("CONNECT가 아니면 Principal을 세팅하지 않는다")
    void ignoresNonConnect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);

        interceptor.preSend(message(accessor), null);

        assertThat(accessor.getUser()).isNull();
    }

    private StompHeaderAccessor connect() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Map<String, Object> handshakeAttrs(String sessionId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(WebSocketConst.SESSION_ID_ATTRIBUTE, sessionId);
        return attrs;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String nameOf(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        return user != null ? user.getName() : null;
    }
}
