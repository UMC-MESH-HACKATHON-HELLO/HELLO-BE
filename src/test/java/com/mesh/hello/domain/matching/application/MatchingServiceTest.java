package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingRoomRepository;
import com.mesh.hello.domain.matching.repository.MatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.reward.application.PointService;
import com.mesh.hello.domain.stt.application.ForbiddenWordDetectedEvent;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.domain.stt.domain.ForbiddenWordDetection;
import com.mesh.hello.domain.stt.repository.ForbiddenWordDetectionRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LiveKitService liveKitService;

    @Mock
    private TranscribeService transcribeService;

    @Mock
    private GeminiSummarizationService geminiSummarizationService;

    @Mock
    private SessionAccountRepository sessionAccountRepository;

    @Mock
    private PointService pointService;

    @Mock
    private ForbiddenWordDetectionRepository forbiddenWordDetectionRepository;

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
                liveKitService,
                transcribeService,
                geminiSummarizationService,
                sessionAccountRepository,
                pointService,
                forbiddenWordDetectionRepository
        );
    }

    /** convertAndSend로 전달된 ApiResponse에서 내부 result(Map)만 추출. */
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
            verify(messagingTemplate).convertAndSend(
                    eq("/api/v1/queue/signal/helpee-1"), noHelperCaptor.capture()
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
                    .convertAndSend(startsWith("/api/v1/queue/signal/"), captor.capture());

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
            verify(messagingTemplate).convertAndSend(
                    eq("/api/v1/queue/signal/helpee-1"), noHelperCaptor.capture()
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
            verify(messagingTemplate).convertAndSend(
                    eq("/api/v1/queue/signal/helper-1"), waitingCaptor.capture()
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
                    .convertAndSend(startsWith("/api/v1/queue/signal/"), captor.capture());
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
    // stopHelperWaiting
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("stopHelperWaiting - 도우미 대기 취소")
    class StopHelperWaitingTest {

        @Test
        @DisplayName("대기 중인 도우미가 취소하면 큐에서 제거된다")
        void removesWaitingHelper() {
            matchingQueueRepository.pushHelper("helper-1");

            matchingService.stopHelperWaiting("helper-1");

            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("대기열에 없는 도우미가 취소하면 NOT_FOUND 예외가 발생한다")
        void notWaitingThrowsNotFound() {
            assertThatThrownBy(() -> matchingService.stopHelperWaiting("stranger"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);

            assertThat(matchingQueueRepository.getWaitingHelperCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("이미 매칭되어 통화 중인 도우미가 취소하면 ALREADY_IN_CALL 예외가 발생하고 방은 유지된다")
        void alreadyInCallThrows() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            assertThatThrownBy(() -> matchingService.stopHelperWaiting("helper-1"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ALREADY_IN_CALL);

            assertThat(matchingRoomRepository.findBySessionId("helper-1")).isPresent();
        }

        @Test
        @DisplayName("stopHelperWaiting과 requestMatch가 동시에 경합해도 취소 성공과 매칭이 동시에 일어나지 않는다")
        void concurrentStopAndMatchAreMutuallyExclusive() throws Exception {
            // 취소 스레드가 락을 계속 선점해 50번 트라이얼 내내 매칭이 한 번도 안 일어날 수도 있는
            // 정상적인 레이스 결과이므로, 스텁 미사용을 오류로 취급하지 않도록 lenient 처리한다.
            lenient().when(liveKitService.createToken(anyString(), anyString())).thenReturn("token");

            int trials = 50;
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            try {
                for (int i = 0; i < trials; i++) {
                    String helper = "race-helper-" + i;
                    String helpee = "race-helpee-" + i;
                    matchingQueueRepository.pushHelper(helper);

                    CountDownLatch start = new CountDownLatch(1);
                    Future<Boolean> cancelSucceeded = executorService.submit(() -> {
                        start.await();
                        try {
                            matchingService.stopHelperWaiting(helper);
                            return true;
                        } catch (BusinessException e) {
                            return false;
                        }
                    });
                    Future<?> matchAttempted = executorService.submit(() -> {
                        start.await();
                        matchingService.requestMatch(helpee);
                        return null;
                    });
                    start.countDown();

                    boolean cancelled = cancelSucceeded.get();
                    matchAttempted.get();
                    boolean matched = matchingRoomRepository.findBySessionId(helper).isPresent();

                    // 취소가 성공했다면 그 도우미가 방금 매칭되어 있어서는 안 된다(반대도 마찬가지).
                    assertThat(cancelled && matched)
                            .as("trial=%d cancelled=%s matched=%s", i, cancelled, matched)
                            .isFalse();
                }
            } finally {
                executorService.shutdown();
            }
        }

        @Test
        @DisplayName("큐에서 pop된 뒤 방 저장 전(LiveKit 토큰 발급 중) 취소를 시도하면 " +
                "NOT_FOUND로 오판하지 않고 ALREADY_IN_CALL을 던진다")
        void cancelDuringInFlightMatchingIsRejectedNotSilentlyMismatched() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");

            CountDownLatch tokenCallStarted = new CountDownLatch(1);
            CountDownLatch proceedWithToken = new CountDownLatch(1);

            given(liveKitService.createToken(anyString(), anyString())).willAnswer(invocation -> {
                tokenCallStarted.countDown();
                proceedWithToken.await();
                return "token";
            });

            ExecutorService executorService = Executors.newSingleThreadExecutor();
            try {
                Future<?> matchFuture = executorService.submit(() -> matchingService.requestMatch("helpee-1"));

                // helper는 이미 큐에서 pop됐지만 아직 방(room)에는 저장되지 않은 순간까지 대기
                tokenCallStarted.await();

                assertThatThrownBy(() -> matchingService.stopHelperWaiting("helper-1"))
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ALREADY_IN_CALL);

                proceedWithToken.countDown();
                matchFuture.get();

                assertThat(matchingRoomRepository.findBySessionId("helper-1")).isPresent();
            } finally {
                executorService.shutdown();
            }
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
                    eq("/api/v1/topic/room/" + roomId), endedCaptor.capture()
            );
            assertThat(resultOf(endedCaptor.getValue())).isEqualTo(Map.of("type", "ENDED"));
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
            assertThat(matchingRoomRepository.findBySessionId("helper-1")).isEmpty();
        }

        @Test
        @DisplayName("로그인된 도우미가 통화 종료 → 포인트 적립")
        void endCallAwardsPointsToLoggedInHelper() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            given(sessionAccountRepository.findUserId("helper-1")).willReturn(Optional.of(42L));
            matchingService.requestMatch("helpee-1");

            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();

            matchingService.endCall("helpee-1", roomId);

            verify(pointService).awardCallCompletePoints(42L, roomId);
        }

        @Test
        @DisplayName("로그인하지 않은 도우미가 통화 종료 → 포인트 미적립")
        void endCallSkipsPointsForAnonymousHelper() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();

            matchingService.endCall("helpee-1", roomId);

            verify(pointService, never()).awardCallCompletePoints(any(), anyString());
        }
    }

    // ─────────────────────────────────────────────────────────
    // onForbiddenWordDetected
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("onForbiddenWordDetected - 금지어 감지 시 강제 종료")
    class OnForbiddenWordDetectedTest {

        @Test
        @DisplayName("금지어 감지 → 감지 이력 저장 + FORCE_ENDED 브로드캐스트 + 방 삭제, 요약/포인트는 없음")
        void forceEndsCallOnDetection() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();
            clearInvocations(messagingTemplate);

            matchingService.onForbiddenWordDetected(
                    new ForbiddenWordDetectedEvent(roomId, "helpee-1", "helpee", "금지어", "그 금지어 발화입니다"));

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(messagingTemplate).convertAndSend(eq("/api/v1/topic/room/" + roomId), captor.capture());
            assertThat(resultOf(captor.getValue()).get("type")).isEqualTo("FORCE_ENDED");

            assertThat(matchingRoomRepository.findByRoomId(roomId)).isEmpty();
            verify(forbiddenWordDetectionRepository).save(any(ForbiddenWordDetection.class));
            verify(geminiSummarizationService, never()).summarizeAndNotify(any(), any(), any(), any());
            verify(pointService, never()).awardCallCompletePoints(any(), anyString());
        }

        @Test
        @DisplayName("이미 종료 처리 중인 방이면 중복 처리하지 않는다")
        void ignoresAlreadyClosingRoom() throws Exception {
            matchingQueueRepository.pushHelper("helper-1");
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();
            matchingRoomRepository.findByRoomId(roomId).get().markClosing();
            clearInvocations(messagingTemplate);

            matchingService.onForbiddenWordDetected(
                    new ForbiddenWordDetectedEvent(roomId, "helpee-1", "helpee", "금지어", "그 금지어 발화입니다"));

            verify(messagingTemplate, never()).convertAndSend(eq("/api/v1/topic/room/" + roomId), any(Object.class));
            verify(forbiddenWordDetectionRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 방이면 아무 동작도 하지 않는다")
        void ignoresUnknownRoom() {
            matchingService.onForbiddenWordDetected(
                    new ForbiddenWordDetectedEvent("no-such-room", "helpee-1", "helpee", "금지어", "발화"));

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            verify(forbiddenWordDetectionRepository, never()).save(any());
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
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
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
            verify(messagingTemplate).convertAndSend(
                    eq("/api/v1/queue/signal/helpee-1"), partnerCaptor.capture()
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
            verify(messagingTemplate).convertAndSend(
                    eq("/api/v1/queue/signal/helper-1"), partnerCaptor.capture()
            );
            assertThat(resultOf(partnerCaptor.getValue())).isEqualTo(Map.of("type", "PARTNER_DISCONNECTED"));
            assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isEmpty();
        }
    }
}
