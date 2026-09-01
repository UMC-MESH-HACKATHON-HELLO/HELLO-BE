package com.mesh.hello.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.mesh.hello.global.websocket.ping.PingController.PongMessage;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;

/**
 * 웹소켓 연결 토대(1단계)가 기능적으로 멀쩡한지를 격리 검증하는 통합 테스트.
 *
 * <p>다른 파트(대기 큐·매칭·LiveKit)가 전혀 없다고 가정하고, ping/pong 한 번으로
 * (1) 연결, (2) 익명 Principal 등록, (3) 개인 큐 라우팅(/user/queue) 세 가지를 동시에 확인한다.</p>
 *
 * <p>실제 서버를 랜덤 포트로 띄우고(@SpringBootTest), 진짜 STOMP 클라이언트로 /ws에 붙어
 * end-to-end로 검증한다. 인터셉터만 따로 보는 단위 테스트보다 "진짜 도는지"를 확실히 보여준다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketConnectionIntegrationTest {

    @LocalServerPort
    private int port;

    private WebSocketStompClient newStompClient() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(transports));
        // pong이 {"sessionId":"..."} JSON으로 오므로 Jackson 컨버터가 필요하다.
        // Spring Boot 4 / Spring 7은 Jackson 3가 기본이라 신규 컨버터를 쓴다.
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private String wsUrl(String query) {
        // withSockJS() 엔드포인트는 http(s) 스킴을 쓴다.
        return "http://localhost:" + port + "/ws" + query;
    }

    /**
     * 시나리오 1 — 클라이언트가 쿼리파라미터로 sessionId를 자칭해도 더 이상 반영되지 않는다.
     * (과거엔 이 값을 그대로 신뢰해 스푸핑에 악용될 수 있었다 — HttpSession 기반으로 바뀐 뒤
     * 클라이언트가 채운 값은 항상 무시되고 서버가 발급한 값만 쓰인다.)
     */
    @Test
    @DisplayName("쿼리파라미터로 자칭한 sessionId는 무시되고 서버 발급 값이 쓰인다")
    void ignoresClientSuppliedSessionIdQueryParam() throws Exception {
        String claimedSessionId = "victim-session-id";
        CompletableFuture<PongMessage> pongFuture = new CompletableFuture<>();

        StompSession session = newStompClient()
            .connectAsync(wsUrl("?sessionId=" + claimedSessionId), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/pong", new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return PongMessage.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                pongFuture.complete((PongMessage) payload);
            }
        });

        session.send("/app/ping", new byte[0]);

        PongMessage pong = pongFuture.get(5, TimeUnit.SECONDS);
        assertThat(pong).isNotNull();
        assertThat(pong.sessionId()).isNotEqualTo(claimedSessionId);
        assertThat(pong.sessionId()).hasSize(36); // 서버가 발급한 UUID

        session.disconnect();
    }

    /**
     * 시나리오 2 — sessionId를 아무것도 주지 않아도, 서버가 UUID를 발급해 연결이 성립하고
     * pong이 비어있지 않은 sessionId로 돌아온다. 인터셉터의 fallback(UUID 발급) 경로 검증.
     */
    @Test
    @DisplayName("sessionId를 주지 않으면 서버가 발급한 sessionId로 pong이 온다")
    void issuesSessionIdWhenAbsent() throws Exception {
        CompletableFuture<PongMessage> pongFuture = new CompletableFuture<>();

        StompSession session = newStompClient()
            .connectAsync(wsUrl(""), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/pong", new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return PongMessage.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                pongFuture.complete((PongMessage) payload);
            }
        });

        session.send("/app/ping", new byte[0]);

        PongMessage pong = pongFuture.get(5, TimeUnit.SECONDS);
        assertThat(pong).isNotNull();
        assertThat(pong.sessionId()).isNotBlank();
        assertThat(pong.sessionId()).isNotEqualTo("unknown");

        session.disconnect();
    }

    /**
     * 시나리오 3 — 서로 다른 세션으로 붙은 두 클라이언트가 각자 본인 것만 받는다.
     * @SendToUser 기반 1:1 배달(다른 사람 메시지가 새지 않음)을 검증한다.
     * 매칭에서 MATCHED를 엉뚱한 사람에게 보내면 끝장이므로 이 격리가 토대의 핵심이다.
     *
     * <p>sessionId는 이제 서버가 발급하므로(클라이언트가 지정 불가) 미리 알 수 없다 —
     * 각자 pong으로 받은 sessionId가 서로 다르다는 사실 자체로 세션이 분리돼 있음을,
     * 그리고 A/B가 각자 본인 pong만 받았다는 사실로 배달 격리를 확인한다.</p>
     */
    @Test
    @DisplayName("두 클라이언트가 각자 본인 것만 받는다 (배달 격리)")
    void deliversOnlyToOwner() throws Exception {
        CompletableFuture<PongMessage> futureA = new CompletableFuture<>();
        CompletableFuture<PongMessage> futureB = new CompletableFuture<>();

        StompSession sessionA = newStompClient()
            .connectAsync(wsUrl(""), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
        StompSession sessionB = newStompClient()
            .connectAsync(wsUrl(""), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        sessionA.subscribe("/user/queue/pong", frameHandler(futureA));
        sessionB.subscribe("/user/queue/pong", frameHandler(futureB));

        sessionA.send("/app/ping", new byte[0]);
        sessionB.send("/app/ping", new byte[0]);

        String sessionIdA = futureA.get(5, TimeUnit.SECONDS).sessionId();
        String sessionIdB = futureB.get(5, TimeUnit.SECONDS).sessionId();

        assertThat(sessionIdA).isNotBlank();
        assertThat(sessionIdB).isNotBlank();
        assertThat(sessionIdA).isNotEqualTo(sessionIdB);

        sessionA.disconnect();
        sessionB.disconnect();
    }

    /**
     * 시나리오 4 — 핸드셰이크 쿼리파라미터와 CONNECT 프레임 헤더에 동시에 sessionId를 자칭해도
     * 둘 다 무시된다. 과거에는 CONNECT 프레임 헤더가 우선 적용됐는데, 그 경로 자체가
     * 핸드셰이크 검증을 우회하는 스푸핑 수단이었기 때문에 지금은 완전히 막혀있어야 한다.
     */
    @Test
    @DisplayName("쿼리파라미터·CONNECT 헤더로 자칭한 sessionId는 둘 다 무시된다")
    void ignoresClientSuppliedSessionIdFromBothQueryAndConnectHeader() throws Exception {
        String fromQuery = "from-query";
        String fromConnectHeader = "from-connect-header";
        CompletableFuture<PongMessage> pongFuture = new CompletableFuture<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("sessionId", fromConnectHeader);

        StompSession session = newStompClient()
            .connectAsync(
                wsUrl("?sessionId=" + fromQuery),
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/queue/pong", frameHandler(pongFuture));
        session.send("/app/ping", new byte[0]);

        PongMessage pong = pongFuture.get(5, TimeUnit.SECONDS);
        assertThat(pong.sessionId()).isNotEqualTo(fromQuery);
        assertThat(pong.sessionId()).isNotEqualTo(fromConnectHeader);
        assertThat(pong.sessionId()).hasSize(36); // 서버가 발급한 UUID

        session.disconnect();
    }

    private StompFrameHandler frameHandler(CompletableFuture<PongMessage> future) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return PongMessage.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                future.complete((PongMessage) payload);
            }
        };
    }
}
