package com.mesh.hello.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.global.websocket.ping.PingController.PongMessage;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * {@link com.mesh.hello.global.websocket.interceptor.SubscriptionAuthorizationInterceptor}의
 * 실제 STOMP 연결 기준 end-to-end 검증.
 *
 * <p>단위 테스트({@code SubscriptionAuthorizationInterceptorTest})는 인터셉터의
 * {@code preSend()}가 예외를 던지는지만 확인한다. 이 예외는 STOMP 채널 인터셉터 체인에서
 * 발생하는 것이라 HTTP MVC용 {@code GlobalExceptionHandler}의 처리 대상이 아니고, 실제
 * 클라이언트가 뭘 겪는지는 별도 경로(StompSubProtocolHandler)가 결정한다.</p>
 *
 * <p>그래서 여기서는 진짜 서버를 띄우고 진짜 STOMP 클라이언트로 붙어, 보안 관점에서 핵심인
 * "거부된 클라이언트가 실제로 메시지를 수신하지 못하는가"를 직접 증명한다. ERROR 프레임의
 * 형식이나 연결 종료 여부는 부가적으로만 확인한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubscriptionAuthorizationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MatchingRoomRepository matchingRoomRepository;

    private WebSocketStompClient newStompClient() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(transports));
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private String wsUrl() {
        return "http://localhost:" + port + "/ws";
    }

    /**
     * 연결 후 자신의 sessionId를 알아낸다(ping/pong 왕복). sessionId는 서버가 발급하므로
     * 클라이언트가 미리 알 수 없다.
     */
    private String connectAndResolveSessionId(StompSession session) throws Exception {
        CompletableFuture<PongMessage> pongFuture = new CompletableFuture<>();
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
        return pongFuture.get(5, TimeUnit.SECONDS).sessionId();
    }

    private StompSession connect(CompletableFuture<StompHeaders> errorFrameFuture) throws Exception {
        return newStompClient()
            .connectAsync(wsUrl(), new StompSessionHandlerAdapter() {
                @Override
                public void handleException(@NonNull StompSession session, StompCommand command,
                                             @NonNull StompHeaders headers, @NonNull byte[] payload,
                                             @NonNull Throwable exception) {
                    if (!errorFrameFuture.isDone()) {
                        errorFrameFuture.complete(headers);
                    }
                }
            })
            .get(5, TimeUnit.SECONDS);
    }

    private StompFrameHandler stringFrameHandler(CompletableFuture<String> future) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                future.complete((String) payload);
            }
        };
    }

    /**
     * 대조군 — 자기 소유의 signal queue는 정상적으로 구독·수신된다.
     */
    @Test
    @DisplayName("본인 소유의 signal queue는 정상 구독하고 발행된 메시지를 수신한다")
    void ownerReceivesOwnSignalMessage() throws Exception {
        StompSession victim = connect(new CompletableFuture<>());
        String victimSessionId = connectAndResolveSessionId(victim);

        CompletableFuture<String> received = new CompletableFuture<>();
        victim.subscribe("/api/v1/queue/signal/" + victimSessionId, stringFrameHandler(received));

        // 구독이 서버에 등록됐음을 보장하기 위해 ping/pong으로 한 번 더 왕복시켜 barrier로 쓴다.
        connectAndResolveSessionId(victim);

        messagingTemplate.convertAndSend("/api/v1/queue/signal/" + victimSessionId, "hello-victim");

        assertThat(received.get(5, TimeUnit.SECONDS)).isEqualTo("hello-victim");

        victim.disconnect();
    }

    /**
     * 핵심 케이스 — 타인의 signal queue를 구독한 공격자는 그 destination에 발행된 메시지를
     * 실제로 수신하지 못한다. (리뷰에서 요청한 "메시지가 실제로 새지 않는다"는 증명)
     */
    @Test
    @DisplayName("타인의 signal queue를 구독해도 발행된 메시지를 수신하지 못한다")
    void strangerNeverReceivesAnotherSessionsSignalMessage() throws Exception {
        StompSession victim = connect(new CompletableFuture<>());
        String victimSessionId = connectAndResolveSessionId(victim);

        CompletableFuture<StompHeaders> attackerErrorFrame = new CompletableFuture<>();
        StompSession attacker = connect(attackerErrorFrame);

        CompletableFuture<String> attackerReceived = new CompletableFuture<>();
        attacker.subscribe("/api/v1/queue/signal/" + victimSessionId, stringFrameHandler(attackerReceived));

        // 공격자의 SUBSCRIBE가 서버에서 거부 처리(ERROR 프레임 수신)될 때까지 기다린다.
        // 이 시점 이후에는 SubscriptionAuthorizationInterceptor가 이미 preSend()에서
        // 예외를 던져 처리를 끝냈음이 보장되므로, 이제 발행해야 레이스 컨디션이 없다.
        attackerErrorFrame.get(5, TimeUnit.SECONDS);

        messagingTemplate.convertAndSend("/api/v1/queue/signal/" + victimSessionId, "leaked-to-attacker");

        assertThatThrownBy(() -> attackerReceived.get(2, TimeUnit.SECONDS))
            .isInstanceOfAny(TimeoutException.class, ExecutionException.class);

        victim.disconnect();
    }

    /**
     * 부가 확인 — 타인의 destination을 구독하려 한 클라이언트는 STOMP ERROR 프레임을 받는다.
     * (핵심은 위 미수신 검증이고, 이건 어떤 형태로든 클라이언트에게 거부가 통지되는지 참고용으로 확인한다.)
     */
    @Test
    @DisplayName("타인의 destination 구독 시도는 STOMP ERROR 프레임으로 통지된다")
    void rejectedSubscriptionReceivesStompErrorFrame() throws Exception {
        CompletableFuture<StompHeaders> attackerErrorFrame = new CompletableFuture<>();
        StompSession attacker = connect(attackerErrorFrame);

        attacker.subscribe("/api/v1/queue/signal/" + UUID.randomUUID(), stringFrameHandler(new CompletableFuture<>()));

        StompHeaders errorHeaders = attackerErrorFrame.get(5, TimeUnit.SECONDS);
        assertThat(errorHeaders).isNotNull();
    }

    /**
     * room/topic도 같은 성격의 검증 — 방 참가자가 아닌 사람은 그 방의 topic에 발행된
     * 메시지를 수신하지 못하고, 참가자는 정상 수신한다.
     */
    @Test
    @DisplayName("방 참가자가 아니면 topic/room에 발행된 메시지를 수신하지 못한다")
    void nonParticipantNeverReceivesRoomTopicMessage() throws Exception {
        StompSession helpee = connect(new CompletableFuture<>());
        String helpeeSessionId = connectAndResolveSessionId(helpee);

        String roomId = "room-" + UUID.randomUUID();
        matchingRoomRepository.save(new MatchingRoom(roomId, helpeeSessionId, "helper-not-connected"));

        // 대조군: 참가자는 정상 수신한다.
        CompletableFuture<String> helpeeReceived = new CompletableFuture<>();
        helpee.subscribe("/api/v1/topic/room/" + roomId, stringFrameHandler(helpeeReceived));
        connectAndResolveSessionId(helpee); // 구독 등록 barrier

        // 공격자: 방 참가자가 아닌데 같은 topic을 구독 시도한다.
        CompletableFuture<StompHeaders> attackerErrorFrame = new CompletableFuture<>();
        StompSession attacker = connect(attackerErrorFrame);
        CompletableFuture<String> attackerReceived = new CompletableFuture<>();
        attacker.subscribe("/api/v1/topic/room/" + roomId, stringFrameHandler(attackerReceived));
        attackerErrorFrame.get(5, TimeUnit.SECONDS); // 거부 처리가 끝날 때까지 대기(barrier)

        messagingTemplate.convertAndSend("/api/v1/topic/room/" + roomId, "room-broadcast");

        assertThat(helpeeReceived.get(5, TimeUnit.SECONDS)).isEqualTo("room-broadcast");
        assertThatThrownBy(() -> attackerReceived.get(2, TimeUnit.SECONDS))
            .isInstanceOfAny(TimeoutException.class, ExecutionException.class);

        helpee.disconnect();
    }
}