package com.mesh.hello.domain.stt.controller;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.domain.stt.dto.AudioChunkMessage;
import com.mesh.hello.domain.stt.dto.SttStartMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Base64;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AudioStreamingController {

    private final TranscribeService transcribeService;
    private final MatchingRoomRepository matchingRoomRepository;

    @MessageMapping("/stt/start")
    public void startStt(Principal principal, @Payload SttStartMessage msg) {
        String sessionId = principal.getName();
        MatchingRoom room = matchingRoomRepository.findBySessionId(sessionId).orElse(null);

        if (room == null || !room.getRoomId().equals(msg.getRoomId())) {
            log.warn("STT 시작 거부 — 통화 중인 방이 아님: {} (요청 room: {})", sessionId, msg.getRoomId());
            return;
        }

        transcribeService.startSession(sessionId, room.getRoomId(), room.roleOf(sessionId));
    }

    @MessageMapping("/audio/stream")
    public void streamAudio(Principal principal, @Payload AudioChunkMessage msg) {
        byte[] audioData = Base64.getDecoder().decode(msg.getAudio());
        transcribeService.sendAudio(principal.getName(), audioData);
    }

    @MessageMapping("/stt/stop")
    public void stopStt(Principal principal) {
        transcribeService.stopSession(principal.getName());
    }
}