package com.mesh.hello.global.websocket.support;

/**
 * WebSocket / STOMP 연결 계층에서 공유하는 상수.
 *
 * <p>sessionId는 {@link com.mesh.hello.global.websocket.interceptor.SessionIdHandshakeInterceptor}가
 * 핸드셰이크 단계에서 서버 측 {@code HttpSession}으로부터만 확정한다. 쿼리파라미터·HTTP 헤더·
 * STOMP CONNECT 프레임 헤더로 클라이언트가 sessionId를 자칭하는 경로는 더 이상 신뢰하지 않는다
 * (타인의 sessionId를 알아내 그 값으로 재접속하면 그 사람 행세를 할 수 있었던 스푸핑 취약점 때문).</p>
 */
public final class WebSocketConst {

    private WebSocketConst() {
    }

    /**
     * 핸드셰이크 단계에서 확정된 sessionId를 STOMP 세션 attributes에 저장할 때 쓰는 키.
     * CONNECT 인터셉터가 Principal을 세팅할 때 이 값만 읽는다(신뢰 가능한 유일한 출처).
     */
    public static final String SESSION_ID_ATTRIBUTE = "ws.sessionId";
}
