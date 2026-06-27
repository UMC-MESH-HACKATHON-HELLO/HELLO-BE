package com.mesh.hello.domain.matching.controller;

import com.mesh.hello.domain.calling.application.TranscriptBufferStore;
import com.mesh.hello.domain.matching.application.MatchingService;
import com.mesh.hello.domain.matching.dto.SignalMessage;
import com.mesh.hello.domain.matching.dto.SttMessage;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SignalingController {
    private final MatchingService matchingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final TranscriptBufferStore transcriptBufferStore;
    private final MatchingRoomRepository matchingRoomRepository;

    @MessageMapping("/help/register")
    public void helperRegister(Principal principal) {
        matchingService.registerHelper(principal.getName());
    }

    @MessageMapping("/help/request")
    public void helpRequest(Principal principal) {
        matchingService.requestMatch(principal.getName());
    }

    @MessageMapping("/call/end")
    public void callEnd(Principal principal, SignalMessage msg) {
        matchingService.endCall(principal.getName(), msg.getRoomId());
    }

    // 오디오 WebSocket(/ws/audio) 연결이 불가한 환경의 폴백 — 프론트엔드 STT 결과를 직접 수신
    @MessageMapping("/stt/append")
    public void appendStt(Principal principal, SttMessage msg) {
        String roomId = msg.getRoomId();
        log.info("[STT 폴백] roomId={} sessionId={} text={}", roomId, principal.getName(), msg.getText());
        matchingRoomRepository.findByRoomId(roomId).ifPresentOrElse(
                room -> transcriptBufferStore.append(roomId, msg.getText()),
                () -> log.warn("[STT 폴백] roomId={}에 해당하는 방 없음", roomId)
        );
    }

    @MessageMapping("/signal/{roomId}")
    public void signal(@DestinationVariable String roomId, SignalMessage msg) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, ApiResponse.ok("시그널을 중계합니다.", msg));
    }
}