package com.mesh.hello.global.websocket.interceptor;

import com.mesh.hello.global.websocket.support.WebSocketConst;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 핸드셰이크 단계에서 익명 sessionId를 확정한다.
 *
 * <p>과거에는 쿼리파라미터({@code ?sessionId=...})나 HTTP 헤더처럼 클라이언트가 자유롭게 채우는
 * 값을 그대로 신뢰했다. 그 값은 {@code POST /session}에서 서버가 발급한 sessionId와 무관하게
 * 클라이언트가 아무 문자열이나 넣을 수 있어서, 다른 세션의 sessionId(통화 상대에게 노출되는 값)를
 * 알아낸 뒤 그 값을 자칭하면 피해자 행세가 가능한 스푸핑 취약점이 있었다.</p>
 *
 * <p>지금은 요청에 실려온 {@link HttpSession}의 id만 신뢰한다. 이 값은 {@code POST /session}에서
 * 서버가 발급해 JSESSIONID 쿠키로 클라이언트에 내려준 것이라, 클라이언트가 임의로 다른 값을
 * 자칭할 수 없다(위조하려면 남의 세션 쿠키 자체를 훔쳐야 하므로 별개의 위협 모델이다).
 * 유효한 HttpSession이 없는 연결(세션 발급 없이 붙는 개발용 클라이언트 등)은 여전히 1회성 UUID를
 * 발급한다 — 이 값은 어떤 기존 세션과도 매칭되지 않으므로 스푸핑 대상이 없다.</p>
 */
@Slf4j
@Component
public class SessionIdHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        String sessionId = resolveSessionId(request);
        attributes.put(WebSocketConst.SESSION_ID_ATTRIBUTE, sessionId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String resolveSessionId(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session != null) {
                log.debug("핸드셰이크 - HttpSession 기반 sessionId 사용: {}", session.getId());
                return session.getId();
            }
        }

        String generated = UUID.randomUUID().toString();
        log.debug("핸드셰이크 - HttpSession 없음, 1회성 sessionId 발급: {}", generated);
        return generated;
    }
}
