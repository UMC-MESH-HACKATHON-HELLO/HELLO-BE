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
 * (1) 연결, (2) 익명 Principal 등록, (3) 개인 큐 라우팅(/user/api/v1/queue) 세 가지를 동시에 확인한다.</p>
 *
 * <p>실제 서버를 랜덤 포트로 띄우고(@SpringBootTest), 진짜 STOMP 클라이언트로 /api/v1/ws에 붙어
 * end-to-end로 검증한다. 인터셉터만 따로 보는 단위 테스트보다 "진짜 도는지"를 확실히 보여준다.</p>
 *
 * <p>목적지 경로는 {@link com.mesh.hello.global.websocket.config.WebSocketMessageBrokerConfig}와
 * 맞춘다: 엔드포인트 {@code /api/v1/ws}, 앱 prefix {@code /api/v1}, 개인 큐
 * {@code /user/api/v1/queue/pong}({@code @SendToUser("/api/v1/queue/pong")}).</p>
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
        return "http://localhost:" + port + "/api/v1/ws" + query;
    }

    /**
     * 시나리오 1 — sessionId를 쿼리파라미터로 직접 주면, pong에 그 값이 그대로 돌아온다.
     * 핸드셰이크 인터셉터의 "쿼리파라미터 우선" 경로 + 개인 큐 배달을 검증한다.
     */
    @Test
    @DisplayName("쿼리파라미터로 준 sessionId가 pong으로 그대로 에코된다")
    void echoesProvidedSessionId() throws Exception {
        String givenSessionId = "test-session-abc";
        CompletableFuture<PongMessage> pongFuture = new CompletableFuture<>();

        StompSession session = newStompClient()
            .connectAsync(wsUrl("?sessionId=" + givenSessionId), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        session.subscribe("/user/api/v1/queue/pong", new StompFrameHandler() {
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

        session.send("/api/v1/ping", new byte[0]);

        PongMessage pong = pongFuture.get(5, TimeUnit.SECONDS);
        assertThat(pong).isNotNull();
        assertThat(pong.sessionId()).isEqualTo(givenSessionId);

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

        session.subscribe("/user/api/v1/queue/pong", new StompFrameHandler() {
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

        session.send("/api/v1/ping", new byte[0]);

        PongMessage pong = pongFuture.get(5, TimeUnit.SECONDS);
        assertThat(pong).isNotNull();
        assertThat(pong.sessionId()).isNotBlank();
        assertThat(pong.sessionId()).isNotEqualTo("unknown");

        session.disconnect();
    }

    /**
     * 시나리오 3 — 서로 다른 sessionId로 붙은 두 클라이언트가 각자 본인 것만 받는다.
     * @SendToUser 기반 1:1 배달(다른 사람 메시지가 새지 않음)을 검증한다.
     * 매칭에서 MATCHED를 엉뚱한 사람에게 보내면 끝장이므로 이 격리가 토대의 핵심이다.
     */
    @Test
    @DisplayName("두 클라이언트가 각자 본인 sessionId만 받는다 (배달 격리)")
    void deliversOnlyToOwner() throws Exception {
        String idA = "session-A";
        String idB = "session-B";
        CompletableFuture<PongMessage> futureA = new CompletableFuture<>();
        CompletableFuture<PongMessage> futureB = new CompletableFuture<>();

        StompSession sessionA = newStompClient()
            .connectAsync(wsUrl("?sessionId=" + idA), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
        StompSession sessionB = newStompClient()
            .connectAsync(wsUrl("?sessionId=" + idB), new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);

        sessionA.subscribe("/user/api/v1/queue/pong", frameHandler(futureA));
        sessionB.subscribe("/user/api/v1/queue/pong", frameHandler(futureB));

        sessionA.send("/api/v1/ping", new byte[0]);
        sessionB.send("/api/v1/ping", new byte[0]);

        assertThat(futureA.get(5, TimeUnit.SECONDS).sessionId()).isEqualTo(idA);
        assertThat(futureB.get(5, TimeUnit.SECONDS).sessionId()).isEqualTo(idB);

        sessionA.disconnect();
        sessionB.disconnect();
    }

    /**
     * 시나리오 4 — 핸드셰이크 쿼리파라미터와 CONNECT 프레임 헤더에 서로 다른 sessionId를 동시에 주면,
     * CONNECT 프레임 헤더가 이긴다. (sessionId 결정 우선순위의 end-to-end 검증)
     */
    @Test
    @DisplayName("CONNECT 헤더 sessionId가 핸드셰이크 쿼리보다 우선한다")
    void connectHeaderOverridesHandshake() throws Exception {
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

        session.subscribe("/user/api/v1/queue/pong", frameHandler(pongFuture));
        session.send("/api/v1/ping", new byte[0]);

        PongMessage pong = pongFuture.get(5, TimeUnit.SECONDS);
        assertThat(pong.sessionId()).isEqualTo(fromConnectHeader);

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
