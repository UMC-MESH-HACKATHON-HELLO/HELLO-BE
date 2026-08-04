package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.MatchingQueueRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ApiResponse;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private final MatchingQueueRepository matchingQueueRepository;
    private final MatchingRoomRepository matchingRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveKitService liveKitService;
    private final TranscribeService transcribeService;
    private final GeminiSummarizationService geminiSummarizationService;

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
     * 도우미(helper)가 스스로 대기열 등록을 취소한다.
     * 이미 매칭되어 통화 중이면 취소할 수 없고, 애초에 대기열에 없었다면 실패로 처리한다.
     */
    public void stopHelperWaiting(String helperSessionId) {
        if (matchingRoomRepository.findBySessionId(helperSessionId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_IN_CALL);
        }
        if (!matchingQueueRepository.isHelperWaiting(helperSessionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        matchingQueueRepository.removeHelper(helperSessionId);
    }

    /**
     * 통화를 정상 종료한다. 방에 있는 양측 모두에게 ENDED를 전송하고 방을 삭제한다.
     */
    public void endCall(String sessionId, String roomId) {
        matchingRoomRepository.findByRoomId(roomId).ifPresent(room -> {
            room.markClosing();
            String transcript = transcribeService.flushTranscript(roomId);
            int durationSec = (int) Duration.between(room.getMatchedAt(), LocalDateTime.now()).getSeconds();
            geminiSummarizationService.markPending(
                    roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), durationSec);
            geminiSummarizationService.summarizeAndNotify(
                    roomId, room.getHelpeeSessionId(), room.getHelperSessionId(), transcript);
        });

        messagingTemplate.convertAndSend(
                "/api/v1/topic/room/" + roomId,
                (Object) ApiResponse.ok("통화가 종료되었습니다.", Map.of("type", "ENDED"))
        );
        matchingRoomRepository.deleteByRoomId(roomId);
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