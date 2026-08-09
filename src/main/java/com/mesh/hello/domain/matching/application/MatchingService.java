package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.MatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.reward.application.PointService;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ApiResponse;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final MatchingQueueRepository matchingQueueRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveKitService liveKitService;
    private final TranscribeService transcribeService;
    private final GeminiSummarizationService geminiSummarizationService;
    private final SessionAccountRepository sessionAccountRepository;
    private final PointService pointService;

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
    public void requestMatch(String helpeeSessionId) {
        String helperSessionId;
        synchronized (helperLock) {
            Optional<String> helperOpt = matchingQueueRepository.popWaitingHelper();
            if (helperOpt.isEmpty()) {
                matchingQueueRepository.pushHelpee(helpeeSessionId);
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
                matchingQueueRepository.pushHelper(helperSessionId);
                helpersBeingMatched.remove(helperSessionId);
            }
            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helpeeSessionId,
                    ApiResponse.ok("대기 중인 도우미가 없습니다.", Map.of("type", "NO_HELPER"))
            );
        }
    }

    /**
     * 도우미(helper)가 대기열에 등록된다.
     * 대기 중인 도움 요청자(helpee)가 있으면 즉시 매칭을 시도한다.
     */
    public void registerHelper(String helperSessionId) {
        Optional<String> helpeeOpt = matchingQueueRepository.popWaitingHelpee();

        if (helpeeOpt.isEmpty()) {
            matchingQueueRepository.pushHelper(helperSessionId);
            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helperSessionId,
                    ApiResponse.ok("대기열에 등록되었습니다.", Map.of("type", "WAITING"))
            );
            return;
        }

        String helpeeSessionId = helpeeOpt.get();
        String roomId = UUID.randomUUID().toString();

        try {
            String helpeeToken = liveKitService.createToken(roomId, helpeeSessionId);
            String helperToken = liveKitService.createToken(roomId, helperSessionId);

            MatchingRoom room = new MatchingRoom(roomId, helpeeSessionId, helperSessionId);
            matchingRoomRepository.save(room);

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
            matchingQueueRepository.pushHelpee(helpeeSessionId);
            matchingQueueRepository.pushHelper(helperSessionId);
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
            if (matchingQueueRepository.removeHelper(helperSessionId)) {
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
     * <p>helper/helpee 양쪽이 거의 동시에 종료를 요청할 수 있으므로, 방 제거는
     * {@link MatchingRoomRepository#deleteByRoomId}의 원자적 제거 결과로 판단해
     * 둘 중 먼저 제거에 성공한 호출만 포인트를 적립한다. 포인트 적립은
     * {@code PointHistory.roomId}의 유니크 제약으로도 한 번만 반영되며, 적립이 실패하더라도
     * ENDED 브로드캐스트는 항상 수행한다.</p>
     */
    public void endCall(String sessionId, String roomId) {
        Optional<MatchingRoom> roomOpt = matchingRoomRepository.findByRoomId(roomId);
        if (roomOpt.isPresent() && !roomOpt.get().contains(sessionId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SESSION);
        }

        roomOpt.ifPresent(room -> {
            room.markClosing();
            String transcript = transcribeService.flushTranscript(roomId);
            int durationSec = (int) Duration.between(room.getMatchedAt(), LocalDateTime.now()).getSeconds();
            geminiSummarizationService.markPending(
                    roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), durationSec);
            geminiSummarizationService.summarizeAndNotify(
                    roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), transcript);

            if (matchingRoomRepository.deleteByRoomId(roomId).isPresent()) {
                awardPointsSafely(room.getHelperSessionId(), roomId);
            }
        });

        messagingTemplate.convertAndSend(
                "/api/v1/topic/room/" + roomId,
                (Object) ApiResponse.ok("통화가 종료되었습니다.", Map.of("type", "ENDED"))
        );
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
     */
    public void handleDisconnect(String sessionId) {
        matchingQueueRepository.removeHelper(sessionId);
        matchingQueueRepository.removeHelpee(sessionId);

        matchingRoomRepository.findBySessionId(sessionId).ifPresent(room -> {
            room.markClosing();
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