package com.mesh.hello.global.websocket.interceptor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Graceful shutdown 중 신규 WebSocket 핸드셰이크를 거부한다.
 *
 * <p>{@link SmartLifecycle#stop()}이 호출되면(컨텍스트 종료 단계) 플래그를 세우고,
 * 이후 들어오는 핸드셰이크는 503으로 막아 종료 중 새 연결이 붙는 것을 방지한다.
 * 인증 의존이 없어 참고코드 패턴을 그대로 차용했다.</p>
 */
@Slf4j
@Component
public class ShutdownAwareHandshakeInterceptor implements HandshakeInterceptor, SmartLifecycle {

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (shuttingDown.get()) {
            log.warn("서버 종료 중 - 신규 WebSocket 핸드셰이크 거부: {}", request.getRemoteAddress());
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        log.info("서버 종료 감지 - 신규 WebSocket 연결을 거부합니다.");
        shuttingDown.set(true);
    }

    @Override
    public boolean isRunning() {
        return !shuttingDown.get();
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE;
    }
}
