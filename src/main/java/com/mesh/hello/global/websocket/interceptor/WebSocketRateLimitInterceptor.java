package com.mesh.hello.global.websocket.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.security.Principal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * SEND 메시지 빈도 제한 인터셉터.
 *
 * <p>참고코드는 회원 {@code Long memberId}를 키로 잡지만, 이 프로젝트는 무인증이므로
 * {@code Principal.getName()}(= 익명 sessionId 문자열)을 키로 잡는다.
 * 초당 {@value #MAX_SEND_PER_SECOND}건을 넘으면 <b>해당 메시지만 드롭</b>하고(세션은 유지),
 * 버킷은 Caffeine TTL로 자동 만료된다. (Redis 미사용 — 단일 인스턴스 인메모리)</p>
 */
@Slf4j
@Component
public class WebSocketRateLimitInterceptor implements ChannelInterceptor {

    private static final int MAX_SEND_PER_SECOND = 20;

    private final Cache<String, AtomicInteger> rateLimitCache = Caffeine.newBuilder()
        .expireAfterWrite(2, TimeUnit.SECONDS)
        .build();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.SEND.equals(accessor.getCommand())) {
            return message;
        }

        String sessionId = extractSessionId(accessor);
        if (sessionId == null) {
            return message;
        }

        long secondBucket = System.currentTimeMillis() / 1000;
        String cacheKey = sessionId + ":" + secondBucket;
        AtomicInteger count = rateLimitCache.get(cacheKey, key -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_SEND_PER_SECOND) {
            log.warn("WebSocket 메시지 전송 빈도 초과: sessionId={}", sessionId);
            return null; // 세션 유지, 해당 메시지만 무시
        }

        return message;
    }

    private String extractSessionId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        return user != null ? user.getName() : null;
    }
}
