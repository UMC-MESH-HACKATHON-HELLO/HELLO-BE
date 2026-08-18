package com.mesh.hello.domain.matching.controller;

import com.mesh.hello.domain.matching.application.MatchingService;
import com.mesh.hello.domain.matching.dto.HelpRequest;
import com.mesh.hello.domain.matching.dto.SignalMessage;
import com.mesh.hello.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class SignalingController {
    private final MatchingService matchingService;
    private final SimpMessagingTemplate messagingTemplate;

    // 도우미 대기열 등록 → 대기 중인 helpee 있으면 즉시 매칭
    @MessageMapping("/help/register")
    public void helperRegister(Principal principal) {
        matchingService.registerHelper(principal.getName());
    }

    // 어르신 도움 요청 → 매칭 시도
    @MessageMapping("/help/request")
    public void helpRequest(
            Principal principal,
            @Payload HelpRequest request
    ) {
        matchingService.requestMatch(principal.getName(), request.category());
    }

    // 통화 종료
    @MessageMapping("/call/end")
    public void callEnd(Principal principal, SignalMessage msg) {
        matchingService.endCall(principal.getName(), msg.getRoomId());
    }

    // WebRTC 시그널링 중계 (SDP/ICE)
    @MessageMapping("/signal/{roomId}")
    public void signal(@DestinationVariable String roomId, SignalMessage msg) {
        messagingTemplate.convertAndSend("/api/v1/topic/room/" + roomId, ApiResponse.ok("시그널을 중계합니다.", msg));
    }
}
