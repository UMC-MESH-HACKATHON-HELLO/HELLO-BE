package com.mesh.hello.domain.matching.controller;

import com.mesh.hello.domain.matching.application.MatchingService;
import com.mesh.hello.domain.matching.dto.SignalMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class SignalingController {
    private final MatchingService matchingService;
    private final SimpMessagingTemplate messagingTemplate;

    // 도우미 대기열 등록 → 대기 중인 helpee 있으면 즉시 매칭
    @MessageMapping("/help/register")
    public void helperRegister(SignalMessage msg) {
        matchingService.registerHelper(msg.getSessionId());
    }

    // 어르신 도움 요청 → 매칭 시도
    @MessageMapping("/help/request")
    public void helpRequest(SignalMessage msg) {
        matchingService.requestMatch(msg.getSessionId());
    }

    // 통화 종료
    @MessageMapping("/call/end")
    public void callEnd(SignalMessage msg) {
        matchingService.endCall(msg.getSessionId(), msg.getRoomId());
    }

    // WebRTC 시그널링 중계 (SDP/ICE)
    @MessageMapping("/signal/{roomId}")
    public void signal(@DestinationVariable String roomId, SignalMessage msg) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, msg);
    }
}
