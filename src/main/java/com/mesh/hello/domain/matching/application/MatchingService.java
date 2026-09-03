package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.MatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.reward.application.PointService;
import com.mesh.hello.domain.stt.application.ForbiddenWordDetectedEvent;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.domain.stt.domain.ForbiddenWordDetection;
import com.mesh.hello.domain.stt.repository.ForbiddenWordDetectionRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ApiResponse;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    // private final MatchingQueueRepository matchingQueueRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveKitService liveKitService;
    private final TranscribeService transcribeService;
    private final GeminiSummarizationService geminiSummarizationService;
    private final SessionAccountRepository sessionAccountRepository;
    private final PointService pointService;
    private final ForbiddenWordDetectionRepository forbiddenWordDetectionRepository;
    private final PriorityMatchingService priorityMatchingService;

    /**
     * 큐에서 pop됐지만 아직 방 저장이 끝나지 않은(LiveKit 토큰 발급 중인) helper 세션 집합.
     * {@code helperLock}으로 상태 전이(pop/mark, remove/unmark)를 직렬화해
     * {@link #stopHelperWaiting}이 "큐에도 없고 방에도 없는" 애매한 순간을 관측하지 않도록 한다.
     */
    private final Set<String> helpersBeingMatched = ConcurrentHashMap.newKeySet();
    private final Object helperLock = new Object();

    /**
     * 도움 요청자(helpee)가 매칭을 요청한다.
     * 대기 중인 도우미(helper)가 있으면 즉시 매칭해 양측에 MATCHED + LiveKit 토큰을 전송한다.
     * 도우미가 없으면 helpee를 대기열에 넣고 NO_HELPER를 전송한다.
     */
    public void requestMatch(
            String helpeeSessionId,
            CallSummary.CallCategory category
    ) {
        String helperSessionId;
        synchronized (helperLock) {

            Optional<String> helperOpt =
                    priorityMatchingService.matchHelper(helpeeSessionId, category);

            if (helperOpt.isEmpty()) {

                messagingTemplate.convertAndSend(
                        "/api/v1/queue/signal/" + helpeeSessionId,
                        ApiResponse.ok("대기 중인 도우미가 없습니다.", Map.of("type", "NO_HELPER"))
                );
                return;
            }

            helperSessionId = helperOpt.get();
            helpersBeingMatched.add(helperSessionId);
        }

        String roomId = UUID.randomUUID().toString();

        try {
            String helpeeToken = liveKitService.createToken(roomId, helpeeSessionId);
            String helperToken = liveKitService.createToken(roomId, helperSessionId);

            MatchingRoom room = new MatchingRoom(roomId, helpeeSessionId, helperSessionId);
            matchingRoomRepository.save(room);
            helpersBeingMatched.remove(helperSessionId);

            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helpeeSessionId,
                    ApiResponse.ok("매칭에 성공했습니다.",
                            Map.of("type", "MATCHED", "roomId", roomId, "token", helpeeToken))
            );
            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helperSessionId,
                    ApiResponse.ok("매칭에 성공했습니다.",
                            Map.of("type", "MATCHED", "roomId", roomId, "token", helperToken))
            );

        } catch (Exception e) {
            // 토큰 발급 실패 시 도우미 다시 큐에 넣고 helpee에게 실패 알림
            synchronized (helperLock) {
                priorityMatchingService.restoreHelper(helperSessionId);
                helpersBeingMatched.remove(helperSessionId);
            }
            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helpeeSessionId,
                    ApiResponse.ok("대기 중인 도우미가 없습니다.", Map.of("type", "NO_HELPER"))
            );
        }
    }

    @Deprecated(since="CallCategory를 함께 전달해주세요.")
    public void requestMatch(
            String helpeeSessionId
    ) {
        requestMatch(helpeeSessionId, CallSummary.CallCategory.ETC);
    }

    /**
     * 도우미(helper)가 대기열에 등록된다.
     * 대기 중인 도움 요청자(helpee)가 있으면 즉시 매칭을 시도한다.
     */
    public void registerHelper(String helperSessionId) {
        Optional<PriorityMatchingService.MatchedHelpee> helpeeOpt;

        synchronized (helperLock) {
            helpeeOpt = priorityMatchingService.registerHelper(helperSessionId);

            if (helpeeOpt.isEmpty()) {
                messagingTemplate.convertAndSend(
                        "/api/v1/queue/signal/" + helperSessionId,
                        ApiResponse.ok("대기열에 등록되었습니다.", Map.of("type", "WAITING"))
                );
                return;
            }

            helpersBeingMatched.add(helperSessionId);
        }

        PriorityMatchingService.MatchedHelpee helpee = helpeeOpt.get();
        String helpeeSessionId = helpee.sessionId();
        String roomId = UUID.randomUUID().toString();

        try {
            String helpeeToken = liveKitService.createToken(roomId, helpeeSessionId);
            String helperToken = liveKitService.createToken(roomId, helperSessionId);

            MatchingRoom room = new MatchingRoom(roomId, helpeeSessionId, helperSessionId);
            matchingRoomRepository.save(room);

            synchronized (helperLock) {
                helpersBeingMatched.remove(helperSessionId);
            }

            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helpeeSessionId,
                    ApiResponse.ok("매칭에 성공했습니다.",
                            Map.of("type", "MATCHED", "roomId", roomId, "token", helpeeToken))
            );
            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helperSessionId,
                    ApiResponse.ok("매칭에 성공했습니다.",
                            Map.of("type", "MATCHED", "roomId", roomId, "token", helperToken))
            );

        } catch (Exception e) {
            synchronized (helperLock) {
                priorityMatchingService.restoreHelpee(helpeeSessionId, helpee.category());
                priorityMatchingService.restoreHelper(helperSessionId);
                helpersBeingMatched.remove(helperSessionId);
            }
        }
    }

    /**
     * 도우미(helper)가 스스로 대기열 등록을 취소한다.
     * 이미 매칭되어 통화 중이면 취소할 수 없고, 애초에 대기열에 없었다면 실패로 처리한다.
     *
     * <p>먼저 큐에서 제거를 "시도"하고 그 결과로 판단한다(제거 성공 여부 확인 후 별도로 제거하지 않음).
     * {@code removeHelper}는 {@link java.util.concurrent.ConcurrentLinkedQueue}의 원자적 CAS 기반이라
     * {@code popWaitingHelper}(매칭 시도)와 동시에 경합해도 둘 중 하나만 성공하는 것이 보장된다.
     * 먼저 존재 여부를 확인한 뒤 별도로 제거하면 그 사이에 매칭이 끼어들어
     * "취소 성공" 응답과 실제 매칭이 동시에 발생하는 경쟁 상태가 생길 수 있다.</p>
     *
     * <p>큐에서는 이미 빠졌지만 아직 방이 저장되지 않은 순간(LiveKit 토큰 발급 중)에는
     * {@code removeHelper}도 실패하고 {@code matchingRoomRepository}에도 없어 자칫 NOT_FOUND로
     * 오판할 수 있다. 이 구간은 {@link #helpersBeingMatched}로 추적해 ALREADY_IN_CALL로 처리한다
     * (취소 실패 응답 뒤에 곧바로 MATCHED 알림이 도착하는 모순을 막기 위함).</p>
     */
    public void stopHelperWaiting(String helperSessionId) {
        synchronized (helperLock) {
            if (priorityMatchingService.removeWaitingHelper(helperSessionId)) {
                return;
            }
            if (helpersBeingMatched.contains(helperSessionId)) {
                throw new BusinessException(ErrorCode.ALREADY_IN_CALL);
            }
        }
        if (matchingRoomRepository.findBySessionId(helperSessionId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_IN_CALL);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    /**
     * 통화를 정상 종료한다. 방에 있는 양측 모두에게 ENDED를 전송하고 방을 삭제한다.
     * 로그인된 도우미였다면 통화 완료 포인트를 적립한다.
     *
     * <p>양쪽이 거의 동시에 종료를 호출하는 경우를 허용하기 위해 방이 이미 없으면(먼저 종료된 경우)
     * 조용히 통과시키지만, 방이 존재하는데 sessionId가 그 방의 참가자가 아니면 거부한다.
     * roomId만 알면(또는 추측하면) 참가자가 아닌 클라이언트가 남의 통화를 강제 종료할 수 있는
     * 문제를 막기 위함이다.</p>
     *
     * <p>helper/helpee 양쪽이 거의 동시에 종료를 요청할 수 있고, 금지어 감지로 인한 강제 종료
     * ({@link #onForbiddenWordDetected})와도 경합할 수 있으므로 {@link MatchingRoom#markClosing()}의
     * 원자적 CAS 결과로 "최초 종료 요청"을 가려낸다. 종료 처리(녹취 flush·요약·포인트 적립·ENDED 발행)는
     * markClosing()에 성공한 호출 하나만 수행한다 — 진 쪽은 이미 이긴 쪽이 같은 topic으로 ENDED를
     * 보내므로 별도 처리가 필요 없고, 무거운 녹취 요약이 중복 실행되는 것도 막는다. 방이 애초에
     * 존재하지 않으면(roomOpt 비어있음) 이 블록에 들어가지 않으므로 존재하지 않는 방으로 ENDED가
     * 새 나가는 일도 없다.</p>
     */
    public void endCall(String sessionId, String roomId) {
        Optional<MatchingRoom> roomOpt = matchingRoomRepository.findByRoomId(roomId);
        if (roomOpt.isPresent() && !roomOpt.get().contains(sessionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SESSION);
        }

        roomOpt.filter(MatchingRoom::markClosing).ifPresent(room -> {
            String transcript = transcribeService.flushTranscript(roomId);
            int durationSec = (int) Duration.between(room.getMatchedAt(), LocalDateTime.now()).getSeconds();
            geminiSummarizationService.markPending(
                    roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), durationSec);
            geminiSummarizationService.summarizeAndNotify(
                    roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), transcript);

            if (matchingRoomRepository.deleteByRoomId(roomId).isPresent()) {
                awardPointsSafely(room.getHelperSessionId(), roomId);
            }

            messagingTemplate.convertAndSend(
                    "/api/v1/topic/room/" + roomId,
                    (Object) ApiResponse.ok("통화가 종료되었습니다.", Map.of("type", "ENDED"))
            );
        });
    }

    /**
     * STT 파이프라인(TranscribeService)에서 금지어가 감지되면 통화를 강제 종료한다.
     * {@code stt} 도메인이 {@code matching}을 직접 참조하지 않도록 이벤트로 디커플링돼 있다.
     *
     * <p>정상 종료({@link #endCall})와 달리 Gemini 요약은 생성하지 않고(금지어가 섞인 대화를
     * 요약에 노출하지 않기 위함), 포인트도 적립하지 않는다. 양측에는 ENDED가 아닌 FORCE_ENDED를
     * 전송해 강제 종료임을 구분한다.</p>
     *
     * <p>Spring의 이벤트 발행은 기본적으로 동기이므로, 이 리스너는 이벤트를 발행한
     * {@code TranscribeService}의 OkHttp WebSocket 리더 스레드에서 그대로 실행된다.
     * {@link #transcribeService}.flushTranscript()가 바로 그 세션 자신의 onClosed 콜백을
     * 최대 3초간 기다리는데, 그 콜백을 처리해야 할 리더 스레드가 이미 여기 묶여 있으면
     * 매번 타임아웃까지 지연돼 "실시간 강제 종료"가 무색해진다. {@code @Async}로 별도 스레드에서
     * 실행해 리더 스레드를 즉시 풀어준다({@link GeminiSummarizationService#summarizeAndNotify}와
     * 동일한 패턴, {@code @EnableAsync}는 {@code HelloApplication}에 설정돼 있음).</p>
     */
    @Async
    @EventListener
    public void onForbiddenWordDetected(ForbiddenWordDetectedEvent event) {
        MatchingRoom room = matchingRoomRepository.findByRoomId(event.roomId()).orElse(null);
        if (room == null || !room.markClosing()) {
            return;
        }

        saveDetectionSafely(event);
        transcribeService.flushTranscript(event.roomId());

        if (matchingRoomRepository.deleteByRoomId(event.roomId()).isEmpty()) {
            return;
        }

        messagingTemplate.convertAndSend(
                "/api/v1/topic/room/" + event.roomId(),
                (Object) ApiResponse.ok("부적절한 발화가 감지되어 통화가 강제 종료되었습니다.",
                        Map.of("type", "FORCE_ENDED", "reason", "FORBIDDEN_WORD"))
        );
    }

    private void saveDetectionSafely(ForbiddenWordDetectedEvent event) {
        try {
            forbiddenWordDetectionRepository.save(new ForbiddenWordDetection(
                    event.roomId(), event.sessionId(), event.role(), event.matchedWord(), event.utterance()));
        } catch (Exception e) {
            log.warn("금지어 감지 이력 저장 실패: roomId={}", event.roomId(), e);
        }
    }

    /**
     * 로그인된 도우미에게 통화 완료 포인트를 적립한다.
     *
     * <p>적립 실패(비로그인, 중복 적립 등)가 ENDED 브로드캐스트를 막지 않도록
     * 예외를 여기서 흡수한다.</p>
     */
    private void awardPointsSafely(String helperSessionId, String roomId) {
        sessionAccountRepository.findUserId(helperSessionId).ifPresent(helperId -> {
            try {
                pointService.awardCallCompletePoints(helperId, roomId);
            } catch (Exception e) {
                log.warn("통화 완료 포인트 적립 실패: helperId={}, roomId={}", helperId, roomId, e);
            }
        });
    }

    /**
     * sessionId가 roomId 방의 실제 참가자인지 검증한다. 방이 없거나 참가자가 아니면 예외를 던진다.
     *
     * <p>{@code /signal/{roomId}}로 임의의 roomId에 SDP/ICE를 주입해 남의 통화에 끼어드는 것을
     * 막기 위해, 시그널을 중계하기 전에 발신자가 그 방의 당사자인지 확인한다.</p>
     */
    public void assertParticipant(String sessionId, String roomId) {
        MatchingRoom room = matchingRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!room.contains(sessionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SESSION);
        }
    }

    /**
     * 세션 연결이 끊겼을 때 정리 작업을 수행한다.
     * 대기열에서 제거하고, 통화 중이었다면 상대방에게 PARTNER_DISCONNECTED를 전송한다.
     *
     * <p>{@link #endCall}과 마찬가지로 {@link MatchingRoom#markClosing()}으로 다른 종료 경로
     * (정상 종료, 금지어 강제 종료)와의 경합을 가른다. 이미 선점됐다면 그쪽이 정리를 책임지므로
     * 여기서는 아무 것도 하지 않는다.</p>
     */
    public void handleDisconnect(String sessionId) {
        priorityMatchingService.removeWaitingParticipant(sessionId);

        matchingRoomRepository.findBySessionId(sessionId).ifPresent(room -> {
            if (!room.markClosing()) {
                return;
            }
            String transcript = transcribeService.flushTranscript(room.getRoomId());
            int durationSec = (int) Duration.between(room.getMatchedAt(), LocalDateTime.now()).getSeconds();
            geminiSummarizationService.markPending(
                    room.getRoomId(), room.getHelpeeSessionId(), room.getHelperSessionId(), durationSec);
            geminiSummarizationService.summarizeAndNotify(
                    room.getRoomId(), room.getHelpeeSessionId(), room.getHelperSessionId(), transcript);

            room.counterpartOf(sessionId).ifPresent(counterpart ->
                    messagingTemplate.convertAndSend(
                            "/api/v1/queue/signal/" + counterpart,
                            ApiResponse.ok("상대방의 연결이 종료되었습니다.", Map.of("type", "PARTNER_DISCONNECTED"))
                    )
            );
            matchingRoomRepository.deleteByRoomId(room.getRoomId());
        });
    }
}
