package com.mesh.hello.global.websocket.interceptor;

import com.mesh.hello.global.websocket.principal.AnonymousPrincipal;
import com.mesh.hello.global.websocket.support.WebSocketConst;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 시점에 익명 Principal을 세팅한다. (연결 계층의 핵심)
 *
 * <p>sessionId 결정 우선순위: CONNECT 프레임 헤더 → 핸드셰이크 attributes → (모두 없으면) UUID 발급.
 * 결정된 sessionId로 {@link AnonymousPrincipal}을 만들어 accessor에 user로 세팅하면,
 * 이후 이 세션의 모든 메시지에 해당 Principal이 따라붙고
 * {@code /user/queue/...} 1:1 라우팅이 sessionId 기준으로 동작한다.</p>
 *
 * <p>다른 인터셉터(rate limit 등)가 Principal을 신뢰할 수 있도록 가장 먼저 실행되어야 하므로
 * {@link Ordered#HIGHEST_PRECEDENCE}로 둔다.</p>
 */
@Slf4j
@Component
public class AnonymousPrincipalChannelInterceptor implements ChannelInterceptor, Ordered {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String sessionId = resolveSessionId(accessor);
        accessor.setUser(new AnonymousPrincipal(sessionId));
        log.debug("익명 Principal 등록: sessionId={}", sessionId);

        return message;
    }

    private String resolveSessionId(StompHeaderAccessor accessor) {
        // 1) CONNECT 프레임 헤더 우선
        String fromFrame = accessor.getFirstNativeHeader(WebSocketConst.SESSION_ID_KEY);
        if (fromFrame != null && !fromFrame.isBlank()) {
            return fromFrame;
        }

        // 2) 핸드셰이크에서 넘어온 attributes
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes != null) {
            Object fromHandshake = attributes.get(WebSocketConst.SESSION_ID_ATTRIBUTE);
            if (fromHandshake instanceof String s && !s.isBlank()) {
                return s;
            }
        }

        // 3) 안전장치 (정상 흐름이면 핸드셰이크에서 이미 발급되어 도달하지 않음)
        String generated = UUID.randomUUID().toString();
        log.warn("CONNECT 시점에 sessionId 부재 - 서버 발급: {}", generated);
        return generated;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
