package com.mesh.hello.global.websocket.config;

import com.mesh.hello.global.websocket.interceptor.AnonymousPrincipalChannelInterceptor;
import com.mesh.hello.global.websocket.interceptor.SessionIdHandshakeInterceptor;
import com.mesh.hello.global.websocket.interceptor.ShutdownAwareHandshakeInterceptor;
import com.mesh.hello.global.websocket.interceptor.SubscriptionAuthorizationInterceptor;
import com.mesh.hello.global.websocket.interceptor.WebSocketRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 무인증 익명 WebSocket·STOMP 연결 계층 설정.
 *
 * <p>참고코드(회원 인증 + Micrometer/Observation + metric/outbound executor)에서
 * 인증·메트릭·관측 관련 요소를 모두 제거하고, 익명 연결에 필요한 최소 구성만 남겼다.</p>
 *
 * <ul>
 *   <li>엔드포인트 {@code /api/v1/ws} (SockJS) + 핸드셰이크 인터셉터 2종(종료거부, sessionId 확정)</li>
 *   <li>심플 브로커 {@code /api/v1/topic}, {@code /api/v1/queue} + heartbeat 4000/4000 + 전용 TaskScheduler</li>
 *   <li>애플리케이션 prefix {@code /api/v1}</li>
 *   <li>인바운드 채널 인터셉터: 익명 Principal 등록 → 구독 인가 검증 → rate limit</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketMessageBrokerConfig implements WebSocketMessageBrokerConfigurer {

    private final SessionIdHandshakeInterceptor sessionIdHandshakeInterceptor;
    private final ShutdownAwareHandshakeInterceptor shutdownAwareHandshakeInterceptor;
    private final AnonymousPrincipalChannelInterceptor anonymousPrincipalChannelInterceptor;
    private final SubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor;
    private final WebSocketRateLimitInterceptor webSocketRateLimitInterceptor;

    /** 심플 브로커 heartbeat 전용 스케줄러. */
    @Bean
    public ThreadPoolTaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/v1/ws")
            .addInterceptors(shutdownAwareHandshakeInterceptor, sessionIdHandshakeInterceptor)
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/api/v1/topic", "/api/v1/queue")
            .setHeartbeatValue(new long[]{4000, 4000})
            .setTaskScheduler(webSocketHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/api/v1");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 순서 중요: Principal 등록(CONNECT) → 구독 인가 검증(SUBSCRIBE) → rate limit(SEND).
        registration.interceptors(
            anonymousPrincipalChannelInterceptor,
            subscriptionAuthorizationInterceptor,
            webSocketRateLimitInterceptor
        );
    }
}
