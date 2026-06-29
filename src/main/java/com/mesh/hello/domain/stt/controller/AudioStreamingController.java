package com.mesh.hello.domain.stt.controller;

import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.domain.stt.dto.AudioChunkMessage;
import com.mesh.hello.domain.stt.dto.SttStartMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Base64;

@Controller
@RequiredArgsConstructor
public class AudioStreamingController {

    private final TranscribeService transcribeService;

    @MessageMapping("/stt/start")
    public void startStt(Principal principal, @Payload SttStartMessage msg) {
        transcribeService.startSession(principal.getName(), msg.getRoomId(), msg.getRole());
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