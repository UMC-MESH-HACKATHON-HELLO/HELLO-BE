package com.mesh.hello.global.websocket.interceptor;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import java.security.Principal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP SUBSCRIBE 인가 검증.
 *
 * <p>{@code /api/v1/queue/signal/{sessionId}}, {@code /api/v1/topic/room/{roomId}},
 * {@code /api/v1/topic/transcript/{roomId}}는 세션/방에 종속된 destination이라, 검증 없이는
 * 임의의 sessionId·roomId를 넣어 구독하는 것만으로 다른 세션의 매칭 결과나 통화 내용을
 * 가로챌 수 있다. 구독 시점에 본인 소유(해당 방의 참가자)인지 확인하고, 아니면 예외를 던져
 * STOMP 연결 자체를 종료시킨다.</p>
 *
 * <p>위 세 prefix 외의 {@code /api/v1/queue/**}, {@code /api/v1/topic/**} destination은
 * 현재 존재하지 않으므로 기본적으로 거부한다(fail-closed) — 검증 로직 갱신 없이
 * 새 세션 종속 destination이 추가되어 인가 없이 노출되는 사고를 막기 위함이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final String QUEUE_SIGNAL_PREFIX = "/api/v1/queue/signal/";
    private static final String TOPIC_ROOM_PREFIX = "/api/v1/topic/room/";
    private static final String TOPIC_TRANSCRIPT_PREFIX = "/api/v1/topic/transcript/";
    private static final String QUEUE_PREFIX = "/api/v1/queue/";
    private static final String TOPIC_PREFIX = "/api/v1/topic/";

    private final MatchingRoomRepository matchingRoomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Principal user = accessor.getUser();
        String sessionId = user != null ? user.getName() : null;
        if (sessionId == null) {
            log.warn("구독 거부 - Principal 없음: destination={}", destination);
            throw new BusinessException(ErrorCode.INVALID_SESSION);
        }

        if (destination.startsWith(QUEUE_SIGNAL_PREFIX)) {
            String owner = destination.substring(QUEUE_SIGNAL_PREFIX.length());
            if (!owner.equals(sessionId)) {
                reject(sessionId, destination);
            }
        } else if (destination.startsWith(TOPIC_ROOM_PREFIX)) {
            assertRoomParticipant(sessionId, destination.substring(TOPIC_ROOM_PREFIX.length()), destination);
        } else if (destination.startsWith(TOPIC_TRANSCRIPT_PREFIX)) {
            assertRoomParticipant(sessionId, destination.substring(TOPIC_TRANSCRIPT_PREFIX.length()), destination);
        } else if (destination.startsWith(QUEUE_PREFIX) || destination.startsWith(TOPIC_PREFIX)) {
            reject(sessionId, destination);
        }

        return message;
    }

    private void assertRoomParticipant(String sessionId, String roomId, String destination) {
        Optional<MatchingRoom> room = matchingRoomRepository.findByRoomId(roomId);
        if (room.isEmpty() || !room.get().contains(sessionId)) {
            reject(sessionId, destination);
        }
    }

    private void reject(String sessionId, String destination) {
        log.warn("구독 거부 - 본인 소유가 아닌 destination: sessionId={}, destination={}", sessionId, destination);
        throw new BusinessException(ErrorCode.FORBIDDEN_SESSION);
    }
}