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
import java.util.UUID;

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
     * 도움 요청자(helpee)가 매칭을 요청한다.
     * 대기 중인 도우미(helper)가 있으면 즉시 매칭해 양측에 MATCHED + LiveKit 토큰을 전송한다.
     * 도우미가 없으면 helpee를 대기열에 넣고 NO_HELPER를 전송한다.
     */
    public void requestMatch(String helpeeSessionId) {
        Optional<String> helperOpt = matchingQueueRepository.popWaitingHelper();

        if (helperOpt.isEmpty()) {
            matchingQueueRepository.pushHelpee(helpeeSessionId);
            messagingTemplate.convertAndSend(
                    "/api/v1/queue/signal/" + helpeeSessionId,
                    ApiResponse.ok("대기 중인 도우미가 없습니다.", Map.of("type", "NO_HELPER"))
            );
            return;
        }

        String helperSessionId = helperOpt.get();
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
            // 토큰 발급 실패 시 도우미 다시 큐에 넣고 helpee에게 실패 알림
            matchingQueueRepository.pushHelper(helperSessionId);
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
     * 통화를 정상 종료한다. 방에 있는 양측 모두에게 ENDED를 전송하고 방을 삭제한다.
     * 로그인된 도우미였다면 통화 완료 포인트를 적립한다.
     *
     * <p>helper/helpee 양쪽이 거의 동시에 종료를 요청할 수 있으므로, 방 제거는
     * {@link MatchingRoomRepository#deleteByRoomId}의 원자적 제거 결과로 판단해
     * 둘 중 먼저 제거에 성공한 호출만 포인트 적립과 ENDED 브로드캐스트를 수행한다.</p>
     *
     * <p>포인트 적립은 {@code PointHistory.roomId}의 유니크 제약으로 같은 방에 대해
     * 한 번만 반영되며, 적립이 실패하더라도 ENDED 브로드캐스트는 항상 수행한다.</p>
     */
    public void endCall(String sessionId, String roomId) {
        MatchingRoom room = matchingRoomRepository.findByRoomId(roomId).orElse(null);
        if (room == null) {
            return;
        }
        if (!room.contains(sessionId)) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED);
        }

        room.markClosing();
        String transcript = transcribeService.flushTranscript(roomId);
        int durationSec = (int) Duration.between(room.getMatchedAt(), LocalDateTime.now()).getSeconds();
        geminiSummarizationService.markPending(
                roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), durationSec);
        geminiSummarizationService.summarizeAndNotify(
                roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), transcript);

        if (matchingRoomRepository.deleteByRoomId(roomId).isEmpty()) {
            return;
        }

        awardPointsSafely(room.getHelperSessionId(), roomId);

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