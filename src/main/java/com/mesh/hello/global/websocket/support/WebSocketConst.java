package com.mesh.hello.global.websocket.support;

/**
 * WebSocket / STOMP 연결 계층에서 공유하는 상수.
 *
 * <p>익명 sessionId를 핸드셰이크와 CONNECT 프레임 양쪽에서 동일한 키로 다루기 위해 한 곳에 모은다.</p>
 */
public final class WebSocketConst {

    private WebSocketConst() {
    }

    /**
     * 클라이언트가 sessionId를 전달할 때 사용하는 키.
     * <ul>
     *   <li>핸드셰이크: 쿼리파라미터({@code ?sessionId=...}) 또는 동일 이름의 HTTP 헤더</li>
     *   <li>STOMP CONNECT: 동일 이름의 프레임 헤더</li>
     * </ul>
     */
    public static final String SESSION_ID_KEY = "sessionId";

    /**
     * 핸드셰이크 단계에서 확정된 sessionId를 STOMP 세션 attributes에 저장할 때 쓰는 키.
     * CONNECT 인터셉터가 프레임 헤더가 없을 때 이 값을 fallback으로 읽는다.
     */
    public static final String SESSION_ID_ATTRIBUTE = "ws.sessionId";
}
