package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingRoomRepository;
import com.mesh.hello.domain.matching.repository.MatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.mesh.hello.global.common.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LiveKitService liveKitService;

    private MatchingQueueRepository matchingQueueRepository;
    private MatchingRoomRepository matchingRoomRepository;
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingQueueRepository = new InMemoryMatchingQueueRepository();
        matchingRoomRepository = new InMemoryMatchingRoomRepository();
        matchingService = new MatchingService(
                matchingQueueRepository,
                matchingRoomRepository,
                messagingTemplate,
                liveKitService
        );
    }

    /** convertAndSend(ToUser)로 전달된 ApiResponse에서 내부 result(Map)만 추출. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> resultOf(Object payload) {
        return (Map<String, String>) ((ApiResponse<?>) payload).getResult();
    }

    // ─────────────────────────────────────────────────────────
    // requestMatch
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("requestMatch - 어르신 도움 요청")
    class RequestMatchTest {

        @Test
        @DisplayName("대기 도우미 없음 → helpee 큐 등록 + NO_HELPER 전송")
        void noHelperAvailable() {
            matchingService.requestMatch("helpee-1");

            assertThat(matchingQueueRepository.getWaitingHelpeeCount()).isEqualTo(1);
            ArgumentCaptor<Object> noHelperCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("helpee-1"), eq("/queue/signal"), noHelperCaptor.capture()
            );
            assertThat(resultOf(noHelperCaptor.getValue())).isEqualTo(Map.of("type", "NO_HELPER"));
        }

        @Test
        @DisplayName("대기 도우미 있음 → 양측 MATCHED + LiveKit 토큰 전송 + 방 생성")
        void helperAvailable() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("token-helpee");
            given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("token-helper");

            matchingService.requestMatch("helpee-1");

            // 대기열 비었는지 확인
            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(0);

            // 양측 MATCHED 수신 확인
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate, times(2))
                    .convertAndSendToUser(anyString(), eq("/queue/signal"), captor.capture());

            List<Map<String, String>> results = captor.getAllValues().stream()
                    .map(MatchingServiceTest::resultOf).toList();
            assertThat(results).allMatch(m -> "MATCHED".equals(m.get("type")));
            assertThat(results.stream().map(m -> m.get("token")))
                    .containsExactlyInAnyOrder("token-helpee", "token-helper");

            // 방 생성 확인
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isPresent();
            assertThat(matchingRoomRepository.findBySessionId("helper-1")).isPresent();
        }

        @Test
        @DisplayName("LiveKit 토큰 발급 실패 → 도우미 큐 복원 + NO_HELPER 전송")
        void liveKitTokenFails() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willThrow(new RuntimeException("LiveKit error"));

            matchingService.requestMatch("helpee-1");

            // 도우미 큐 복원
            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(1);
            // 방 생성 안 됨
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
            // helpee에게 실패 알림
            ArgumentCaptor<Object> noHelperCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("helpee-1"), eq("/queue/signal"), noHelperCaptor.capture()
            );
            assertThat(resultOf(noHelperCaptor.getValue())).isEqualTo(Map.of("type", "NO_HELPER"));
        }
    }

    // ─────────────────────────────────────────────────────────
    // registerHelper
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("registerHelper - 도우미 등록")
    class RegisterHelperTest {

        @Test
        @DisplayName("대기 helpee 없음 → helper 큐 등록 + WAITING 전송")
        void noHelpeeAvailable() {
            matchingService.registerHelper("helper-1");

            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(1);
            ArgumentCaptor<Object> waitingCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("helper-1"), eq("/queue/signal"), waitingCaptor.capture()
            );
            assertThat(resultOf(waitingCaptor.getValue())).isEqualTo(Map.of("type", "WAITING"));
        }

        @Test
        @DisplayName("대기 helpee 있음 → 양측 MATCHED + LiveKit 토큰 전송 + 방 생성")
        void helpeeAvailable() throws Exception {
            matchingQueueRepository.pushHelpee("helpee-1");
            given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("token-helpee");
            given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("token-helper");

            matchingService.registerHelper("helper-1");

            assertThat(matchingQueueRepository.getWaitingHelpeeCount()).isEqualTo(0);
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isPresent();
            assertThat(matchingRoomRepository.findBySessionId("helper-1")).isPresent();

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate, times(2))
                    .convertAndSendToUser(anyString(), eq("/queue/signal"), captor.capture());
            assertThat(captor.getAllValues().stream().map(MatchingServiceTest::resultOf))
                    .allMatch(m -> "MATCHED".equals(m.get("type")));
        }

        @Test
        @DisplayName("LiveKit 토큰 발급 실패 → 양측 모두 큐 복원")
        void liveKitTokenFailsOnRegister() throws Exception {
            matchingQueueRepository.pushHelpee("helpee-1");
            given(liveKitService.createToken(anyString(), anyString())).willThrow(new RuntimeException("LiveKit error"));

            matchingService.registerHelper("helper-1");

            assertThat(matchingQueueRepository.getWaitingHelpeeCount()).isEqualTo(1);
            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(1);
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────
    // endCall
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("endCall - 통화 종료")
    class EndCallTest {

        @Test
        @DisplayName("통화 종료 → /topic/room/{roomId} 에 ENDED 브로드캐스트 + 방 삭제")
        void endCallBroadcastsAndDeletesRoom() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            Optional<MatchingRoom> roomOpt = matchingRoomRepository.findBySessionId("helpee-1");
            assertThat(roomOpt).isPresent();
            String roomId = roomOpt.get().getRoomId();

            matchingService.endCall("helpee-1", roomId);

            ArgumentCaptor<Object> endedCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room/" + roomId), endedCaptor.capture()
            );
            assertThat(resultOf(endedCaptor.getValue())).isEqualTo(Map.of("type", "ENDED"));
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
            assertThat(matchingRoomRepository.findBySessionId("helper-1")).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────
    // handleDisconnect
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("handleDisconnect - 세션 종료")
    class HandleDisconnectTest {

        @Test
        @DisplayName("대기열에만 있던 helper 연결 종료 → 큐에서 제거, 알림 없음")
        void disconnectHelperInQueue() {
            matchingQueueRepository.pushHelper("helper-1");

            matchingService.handleDisconnect("helper-1");

            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(0);
            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("통화 중 helper 연결 종료 → helpee에게 PARTNER_DISCONNECTED + 방 삭제")
        void disconnectHelperInRoom() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("t1");
            given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("t2");
            matchingService.requestMatch("helpee-1");

            // MATCHED 메시지 리셋 후 disconnect 검증
            clearInvocations(messagingTemplate);

            matchingService.handleDisconnect("helper-1");

            ArgumentCaptor<Object> partnerCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("helpee-1"), eq("/queue/signal"), partnerCaptor.capture()
            );
            assertThat(resultOf(partnerCaptor.getValue())).isEqualTo(Map.of("type", "PARTNER_DISCONNECTED"));
            assertThat(matchingRoomRepository.findBySessionId("helper-1")).isEmpty();
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
        }

        @Test
        @DisplayName("통화 중 helpee 연결 종료 → helper에게 PARTNER_DISCONNECTED + 방 삭제")
        void disconnectHelpeeInRoom() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("t1");
            given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("t2");
            matchingService.requestMatch("helpee-1");

            clearInvocations(messagingTemplate);

            matchingService.handleDisconnect("helpee-1");

            ArgumentCaptor<Object> partnerCaptor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSendToUser(
                    eq("helper-1"), eq("/queue/signal"), partnerCaptor.capture()
            );
            assertThat(resultOf(partnerCaptor.getValue())).isEqualTo(Map.of("type", "PARTNER_DISCONNECTED"));
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
        }
    }
}