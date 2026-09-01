package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingRoomRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.reward.application.PointService;
import com.mesh.hello.domain.stt.application.ForbiddenWordDetectedEvent;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.domain.stt.domain.ForbiddenWordDetection;
import com.mesh.hello.domain.stt.repository.ForbiddenWordDetectionRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ApiResponse;
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private LiveKitService liveKitService;
    @Mock private TranscribeService transcribeService;
    @Mock private GeminiSummarizationService geminiSummarizationService;
    @Mock private SessionAccountRepository sessionAccountRepository;
    @Mock private PointService pointService;
    @Mock private ForbiddenWordDetectionRepository forbiddenWordDetectionRepository;
    @Mock private PriorityMatchingService priorityMatchingService;

    private MatchingRoomRepository matchingRoomRepository;
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingRoomRepository = new InMemoryMatchingRoomRepository();
        matchingService = new MatchingService(
                matchingRoomRepository,
                messagingTemplate,
                liveKitService,
                transcribeService,
                geminiSummarizationService,
                sessionAccountRepository,
                pointService,
                forbiddenWordDetectionRepository,
                priorityMatchingService
        );
    }

    @Test
    @DisplayName("대기 helper가 없으면 우선순위 큐에 helpee를 등록하고 대기 알림을 보낸다")
    void requestMatch_waitsWhenNoHelperExists() {
        given(priorityMatchingService.matchHelper(
                "helpee-1", CallSummary.CallCategory.KIOSK
        )).willReturn(Optional.empty());

        matchingService.requestMatch("helpee-1", CallSummary.CallCategory.KIOSK);

        verify(priorityMatchingService).matchHelper("helpee-1", CallSummary.CallCategory.KIOSK);
        assertQueueSignal("helpee-1", "NO_HELPER");
    }

    @Test
    @DisplayName("우선순위 큐에서 선택된 helper와 방을 만들고 양측에 토큰을 보낸다")
    void requestMatch_matchesSelectedHelper() throws Exception {
        given(priorityMatchingService.matchHelper(anyString(), eq(CallSummary.CallCategory.SMARTPHONE)))
                .willReturn(Optional.of("helper-1"));
        given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("helpee-token");
        given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("helper-token");

        matchingService.requestMatch("helpee-1", CallSummary.CallCategory.SMARTPHONE);

        assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isPresent();
        assertThat(matchingRoomRepository.findBySessionId("helper-1")).isPresent();
    }

    @Test
    @DisplayName("방 생성 실패 시 선택된 helper를 우선순위 큐에 복구한다")
    void requestMatch_restoresHelperWhenTokenCreationFails() throws Exception {
        given(priorityMatchingService.matchHelper(anyString(), eq(CallSummary.CallCategory.ROAD_GUIDE)))
                .willReturn(Optional.of("helper-1"));
        given(liveKitService.createToken(anyString(), anyString()))
                .willThrow(new RuntimeException("LiveKit error"));

        matchingService.requestMatch("helpee-1", CallSummary.CallCategory.ROAD_GUIDE);

        verify(priorityMatchingService).restoreHelper("helper-1");
        assertQueueSignal("helpee-1", "NO_HELPER");
    }

    @Test
    @DisplayName("대기 helpee가 없으면 helper가 우선순위 큐에 등록된 상태로 대기 알림을 받는다")
    void registerHelper_waitsWhenNoHelpeeExists() {
        given(priorityMatchingService.registerHelper("helper-1")).willReturn(Optional.empty());

        matchingService.registerHelper("helper-1");

        assertQueueSignal("helper-1", "WAITING");
    }

    @Test
    @DisplayName("방 생성 실패 시 helpee와 helper를 원래 카테고리로 복구한다")
    void registerHelper_restoresBothParticipantsWhenTokenCreationFails() throws Exception {
        PriorityMatchingService.MatchedHelpee helpee = new PriorityMatchingService.MatchedHelpee(
                "helpee-1", CallSummary.CallCategory.KIOSK
        );
        given(priorityMatchingService.registerHelper("helper-1")).willReturn(Optional.of(helpee));
        given(liveKitService.createToken(anyString(), anyString()))
                .willThrow(new RuntimeException("LiveKit error"));

        matchingService.registerHelper("helper-1");

        verify(priorityMatchingService).restoreHelpee("helpee-1", CallSummary.CallCategory.KIOSK);
        verify(priorityMatchingService).restoreHelper("helper-1");
    }

    /** convertAndSend로 전달된 ApiResponse에서 내부 result(Map)만 추출. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> resultOf(Object payload) {
        return (Map<String, String>) ((ApiResponse<?>) payload).getResult();
    }

    private void assertQueueSignal(String sessionId, String type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/api/v1/queue/signal/" + sessionId), captor.capture()
        );
        assertThat(resultOf(captor.getValue())).containsEntry("type", type);
    }

    /** requestMatch("helpee-1")(카테고리 미지정, ETC로 폴백)가 helper-1과 즉시 매칭되도록 스텁한다. */
    private void stubImmediateMatchWithHelper1() {
        given(priorityMatchingService.matchHelper(anyString(), any())).willReturn(Optional.of("helper-1"));
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
            given(priorityMatchingService.removeWaitingHelper("helper-1")).willReturn(true);

            matchingService.stopHelperWaiting("helper-1");

            verify(priorityMatchingService).removeWaitingHelper("helper-1");
        }

        @Test
        @DisplayName("대기열에 없는 도우미가 취소하면 NOT_FOUND 예외가 발생한다")
        void notWaitingThrowsNotFound() {
            assertThatThrownBy(() -> matchingService.stopHelperWaiting("stranger"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("이미 매칭되어 통화 중인 도우미가 취소하면 ALREADY_IN_CALL 예외가 발생하고 방은 유지된다")
        void alreadyInCallThrows() throws Exception {
            stubImmediateMatchWithHelper1();
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

            // priorityMatchingService는 모킹 대상이라 실제 큐 상태가 없으므로,
            // 트라이얼마다 대기 중인 helper 하나를 담아두는 최소한의 가짜 큐로 대체한다.
            Set<String> waitingHelpers = ConcurrentHashMap.newKeySet();
            lenient().when(priorityMatchingService.matchHelper(anyString(), any())).thenAnswer(invocation -> {
                for (String helper : waitingHelpers) {
                    if (waitingHelpers.remove(helper)) {
                        return Optional.of(helper);
                    }
                }
                return Optional.empty();
            });
            lenient().when(priorityMatchingService.removeWaitingHelper(anyString())).thenAnswer(invocation ->
                    waitingHelpers.remove(invocation.getArgument(0))
            );

            int trials = 50;
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            try {
                for (int i = 0; i < trials; i++) {
                    String helper = "race-helper-" + i;
                    String helpee = "race-helpee-" + i;
                    waitingHelpers.add(helper);

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
            stubImmediateMatchWithHelper1();

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
            stubImmediateMatchWithHelper1();
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
            stubImmediateMatchWithHelper1();
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
            stubImmediateMatchWithHelper1();
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();

            matchingService.endCall("helpee-1", roomId);

            verify(pointService, never()).awardCallCompletePoints(any(), anyString());
        }

        @Test
        @DisplayName("참가자가 아닌 sessionId로 종료를 시도하면 FORBIDDEN_SESSION 예외가 발생하고 방은 유지된다")
        void endCallByNonParticipantIsForbidden() throws Exception {
            stubImmediateMatchWithHelper1();
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            Optional<MatchingRoom> roomOpt = matchingRoomRepository.findBySessionId("helpee-1");
            assertThat(roomOpt).isPresent();
            String roomId = roomOpt.get().getRoomId();
            clearInvocations(messagingTemplate);

            assertThatThrownBy(() -> matchingService.endCall("stranger", roomId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN_SESSION);

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            assertThat(matchingRoomRepository.findByRoomId(roomId)).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 roomId로 종료를 시도하면 조용히 통과하고 브로드캐스트도 하지 않는다")
        void endCallOnAlreadyEndedRoomIsNoop() {
            matchingService.endCall("helpee-1", "no-such-room");

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("이미 다른 경로(예: handleDisconnect)가 종료를 선점한 방이면 종료 처리를 건너뛴다")
        void endCallSkipsWhenAlreadyMarkedClosing() throws Exception {
            stubImmediateMatchWithHelper1();
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");

            MatchingRoom room = matchingRoomRepository.findBySessionId("helpee-1").orElseThrow();
            room.markClosing(); // 다른 경로가 이미 종료 처리를 선점했다고 가정
            clearInvocations(messagingTemplate);

            matchingService.endCall("helpee-1", room.getRoomId());

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            verify(pointService, never()).awardCallCompletePoints(any(), anyString());
            assertThat(matchingRoomRepository.findByRoomId(room.getRoomId())).isPresent();
        }
    }

    // ─────────────────────────────────────────────────────────
    // assertParticipant
    // ─────────────────────────────────────────────────────────
    @Nested
    @DisplayName("assertParticipant - 방 참가자 검증")
    class AssertParticipantTest {

        @Test
        @DisplayName("참가자면 예외 없이 통과한다")
        void passesForParticipant() throws Exception {
            stubImmediateMatchWithHelper1();
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");
            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();

            matchingService.assertParticipant("helpee-1", roomId);
            matchingService.assertParticipant("helper-1", roomId);
        }

        @Test
        @DisplayName("참가자가 아니면 FORBIDDEN_SESSION 예외가 발생한다")
        void throwsForNonParticipant() throws Exception {
            stubImmediateMatchWithHelper1();
            given(liveKitService.createToken(anyString(), anyString())).willReturn("token");
            matchingService.requestMatch("helpee-1");
            String roomId = matchingRoomRepository.findBySessionId("helpee-1").get().getRoomId();

            assertThatThrownBy(() -> matchingService.assertParticipant("stranger", roomId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN_SESSION);
        }

        @Test
        @DisplayName("존재하지 않는 roomId면 NOT_FOUND 예외가 발생한다")
        void throwsForNonExistentRoom() {
            assertThatThrownBy(() -> matchingService.assertParticipant("helpee-1", "no-such-room"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
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
            stubImmediateMatchWithHelper1();
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
            stubImmediateMatchWithHelper1();
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
            matchingService.handleDisconnect("helper-1");

            verify(priorityMatchingService).removeWaitingParticipant("helper-1");
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        }

        @Test
        @DisplayName("통화 중 helper 연결 종료 → helpee에게 PARTNER_DISCONNECTED + 방 삭제")
        void disconnectHelperInRoom() throws Exception {
            stubImmediateMatchWithHelper1();
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
            stubImmediateMatchWithHelper1();
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

        @Test
        @DisplayName("다른 종료 경로(예: 금지어 강제 종료)가 이미 방을 선점했다면 handleDisconnect는 아무 것도 하지 않는다")
        void skipsWhenRoomAlreadyClosedByAnotherPath() throws Exception {
            stubImmediateMatchWithHelper1();
            given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("t1");
            given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("t2");
            matchingService.requestMatch("helpee-1");

            String roomId = matchingRoomRepository.findBySessionId("helper-1").get().getRoomId();
            matchingRoomRepository.findByRoomId(roomId).get().markClosing();
            clearInvocations(messagingTemplate);

            matchingService.handleDisconnect("helper-1");

            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
            verify(geminiSummarizationService, never()).summarizeAndNotify(any(), any(), any(), any());
            assertThat(matchingRoomRepository.findByRoomId(roomId)).isPresent();
        }
    }
}
