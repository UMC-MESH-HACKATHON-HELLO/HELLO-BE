package com.mesh.hello.global.websocket.interceptor;

import com.mesh.hello.global.websocket.support.WebSocketConst;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 핸드셰이크 단계에서 익명 sessionId를 확정한다.
 *
 * <p>우선순위: 쿼리파라미터({@code ?sessionId=...}) → HTTP 헤더 → (없으면) 서버가 UUID 발급.
 * 확정된 값을 핸드셰이크 attributes에 저장하면 STOMP 세션 attributes로 넘어가고,
 * CONNECT 인터셉터가 프레임 헤더가 없을 때 이 값을 fallback으로 사용한다.</p>
 */
@Slf4j
@Component
public class SessionIdHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        String sessionId = resolveSessionId(request);
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            log.debug("핸드셰이크에 sessionId 없음 - 서버 발급: {}", sessionId);
        } else {
            log.debug("핸드셰이크에서 sessionId 수신: {}", sessionId);
        }

        attributes.put(WebSocketConst.SESSION_ID_ATTRIBUTE, sessionId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String resolveSessionId(ServerHttpRequest request) {
        // 1) 쿼리파라미터
        String fromQuery = UriComponentsBuilder.fromUri(request.getURI())
            .build()
            .getQueryParams()
            .getFirst(WebSocketConst.SESSION_ID_KEY);
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery;
        }

        // 2) HTTP 헤더
        return request.getHeaders().getFirst(WebSocketConst.SESSION_ID_KEY);
    }
}
