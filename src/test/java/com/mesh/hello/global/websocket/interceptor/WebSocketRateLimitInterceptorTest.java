package com.mesh.hello.global.websocket.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import com.mesh.hello.global.websocket.principal.AnonymousPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link WebSocketRateLimitInterceptor} 단위 테스트.
 *
 * <p>Spring 컨텍스트 없이 인터셉터를 직접 호출해 "익명 sessionId 기준" 빈도 제한이
 * 실제로 메시지를 드롭하는지(세션은 유지) 검증한다.</p>
 */
class WebSocketRateLimitInterceptorTest {

    private final WebSocketRateLimitInterceptor interceptor = new WebSocketRateLimitInterceptor();

    /**
     * SEND를 같은 sessionId로 빠르게 100건 보내면 초당 한도(20)에 막혀 상당수가 드롭된다.
     * 타이트 루프는 1초 경계를 거의 넘지 않으므로(넘어도 버킷당 20),
     * "통과 수는 20~40 사이"라는 견고한 경계로 단언한다.
     */
    @Test
    @DisplayName("초당 한도를 넘는 SEND는 드롭된다 (sessionId 기준)")
    void dropsWhenExceedingLimit() {
        int passed = 0;
        for (int i = 0; i < 100; i++) {
            if (interceptor.preSend(sendMessage("session-X"), null) != null) {
                passed++;
            }
        }
        assertThat(passed).isBetween(20, 40);
    }

    /**
     * sessionId가 다르면 키가 분리되므로 서로의 한도에 영향을 주지 않는다.
     */
    @Test
    @DisplayName("서로 다른 sessionId는 한도를 따로 센다")
    void limitsPerSessionIndependently() {
        boolean firstOfA = interceptor.preSend(sendMessage("A"), null) != null;
        boolean firstOfB = interceptor.preSend(sendMessage("B"), null) != null;

        assertThat(firstOfA).isTrue();
        assertThat(firstOfB).isTrue();
    }

    /**
     * SEND가 아닌 명령(예: CONNECT)은 빈도 제한 대상이 아니므로 항상 통과한다.
     */
    @Test
    @DisplayName("SEND가 아닌 프레임은 제한하지 않는다")
    void ignoresNonSendFrames() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setUser(new AnonymousPrincipal("session-X"));
        Message<byte[]> connect =
            MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        for (int i = 0; i < 100; i++) {
            assertThat(interceptor.preSend(connect, null)).isNotNull();
        }
    }

    /**
     * Principal이 없으면(키를 만들 수 없으면) 제한 없이 통과시킨다.
     */
    @Test
    @DisplayName("Principal이 없는 SEND는 통과한다")
    void passesWhenNoPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        Message<byte[]> send =
            MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(send, null)).isNotNull();
    }

    private Message<byte[]> sendMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setUser(new AnonymousPrincipal(sessionId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
