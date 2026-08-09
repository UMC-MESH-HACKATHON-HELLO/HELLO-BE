package com.mesh.hello.global.websocket.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingRoomRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import com.mesh.hello.global.websocket.principal.AnonymousPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link SubscriptionAuthorizationInterceptor} 단위 테스트.
 *
 * <p>SUBSCRIBE 시점에 본인 소유가 아닌 sessionId/roomId를 가로채 구독하려는 시도가
 * {@link BusinessException}(FORBIDDEN_SESSION)으로 차단되는지 검증한다.</p>
 */
class SubscriptionAuthorizationInterceptorTest {

    private MatchingRoomRepository matchingRoomRepository;
    private SubscriptionAuthorizationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        matchingRoomRepository = new InMemoryMatchingRoomRepository();
        interceptor = new SubscriptionAuthorizationInterceptor(matchingRoomRepository);
    }

    @Test
    @DisplayName("본인 sessionId의 queue/signal 구독은 허용된다")
    void allowsOwnQueueSignalSubscription() {
        Message<byte[]> subscribe = subscribeMessage("helpee-1", "/api/v1/queue/signal/helpee-1");

        assertThat(interceptor.preSend(subscribe, null)).isSameAs(subscribe);
    }

    @Test
    @DisplayName("다른 sessionId의 queue/signal 구독은 거부된다")
    void rejectsOtherSessionsQueueSignalSubscription() {
        Message<byte[]> subscribe = subscribeMessage("stranger", "/api/v1/queue/signal/helpee-1");

        assertThatThrownBy(() -> interceptor.preSend(subscribe, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN_SESSION);
    }

    @Test
    @DisplayName("방 참가자는 topic/room, topic/transcript를 구독할 수 있다")
    void allowsParticipantToSubscribeToRoomTopics() {
        MatchingRoom room = new MatchingRoom("room-1", "helpee-1", "helper-1");
        matchingRoomRepository.save(room);

        assertThat(interceptor.preSend(
                subscribeMessage("helpee-1", "/api/v1/topic/room/room-1"), null)).isNotNull();
        assertThat(interceptor.preSend(
                subscribeMessage("helper-1", "/api/v1/topic/transcript/room-1"), null)).isNotNull();
    }

    @Test
    @DisplayName("방 참가자가 아니면 topic/room 구독이 거부된다")
    void rejectsNonParticipantRoomTopicSubscription() {
        MatchingRoom room = new MatchingRoom("room-1", "helpee-1", "helper-1");
        matchingRoomRepository.save(room);

        Message<byte[]> subscribe = subscribeMessage("stranger", "/api/v1/topic/room/room-1");

        assertThatThrownBy(() -> interceptor.preSend(subscribe, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN_SESSION);
    }

    @Test
    @DisplayName("존재하지 않는 roomId의 topic/room 구독은 거부된다")
    void rejectsSubscriptionToNonExistentRoom() {
        Message<byte[]> subscribe = subscribeMessage("helpee-1", "/api/v1/topic/room/no-such-room");

        assertThatThrownBy(() -> interceptor.preSend(subscribe, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN_SESSION);
    }

    @Test
    @DisplayName("알 수 없는 queue/topic destination은 fail-closed로 거부된다")
    void rejectsUnknownSessionScopedDestination() {
        Message<byte[]> subscribe = subscribeMessage("helpee-1", "/api/v1/topic/unknown/foo");

        assertThatThrownBy(() -> interceptor.preSend(subscribe, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN_SESSION);
    }

    @Test
    @DisplayName("Principal이 없는 SUBSCRIBE는 INVALID_SESSION으로 거부된다")
    void rejectsSubscriptionWithoutPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/api/v1/queue/signal/helpee-1");
        Message<byte[]> subscribe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(subscribe, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SESSION);
    }

    @Test
    @DisplayName("SUBSCRIBE가 아닌 프레임은 검증하지 않는다")
    void ignoresNonSubscribeFrames() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setUser(new AnonymousPrincipal("stranger"));
        accessor.setDestination("/api/v1/queue/signal/helpee-1");
        Message<byte[]> send = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(interceptor.preSend(send, null)).isSameAs(send);
    }

    private Message<byte[]> subscribeMessage(String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setUser(new AnonymousPrincipal(sessionId));
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}