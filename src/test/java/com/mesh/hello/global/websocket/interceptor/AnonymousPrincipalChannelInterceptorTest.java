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
 * <p>CONNECT 시점에 sessionId 결정 우선순위
 * (프레임 헤더 → 핸드셰이크 attributes → UUID 발급)대로 익명 Principal이 세팅되는지 검증한다.</p>
 */
class AnonymousPrincipalChannelInterceptorTest {

    private final AnonymousPrincipalChannelInterceptor interceptor =
        new AnonymousPrincipalChannelInterceptor();

    /**
     * 프레임 헤더와 핸드셰이크 값이 둘 다 있으면 프레임 헤더가 이긴다.
     */
    @Test
    @DisplayName("CONNECT 프레임 헤더 sessionId가 핸드셰이크 값보다 우선한다")
    void frameHeaderWinsOverHandshake() {
        StompHeaderAccessor accessor = connect();
        accessor.setNativeHeader(WebSocketConst.SESSION_ID_KEY, "fromFrame");
        accessor.setSessionAttributes(handshakeAttrs("fromHandshake"));

        interceptor.preSend(message(accessor), null);

        assertThat(nameOf(accessor)).isEqualTo("fromFrame");
    }

    /**
     * 프레임 헤더가 없으면 핸드셰이크에서 넘어온 값을 사용한다.
     */
    @Test
    @DisplayName("프레임 헤더가 없으면 핸드셰이크 sessionId를 사용한다")
    void fallsBackToHandshake() {
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
