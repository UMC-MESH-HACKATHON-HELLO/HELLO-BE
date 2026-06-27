package com.mesh.hello.domain.communication.controller;

import com.mesh.hello.domain.communication.domain.CallSession;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SignalHandler {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendMatched(CallSession call) {
        Map<String, Object> helpeePayload = Map.of(
                "roomId", call.getRoomId(),
                "token", call.getHelpeeToken()
        );
        messagingTemplate.convertAndSend(
                (String) ("/topic/session/" + call.getHelpeeId()),
                (Object) Map.of("type", "MATCHED", "payload", helpeePayload)
        );

        Map<String, Object> helperPayload = Map.of(
                "roomId", call.getRoomId(),
                "token", call.getHelperToken()
        );
        messagingTemplate.convertAndSend(
                (String) ("/topic/session/" + call.getHelperId()),
                (Object) Map.of("type", "MATCHED", "payload", helperPayload)
        );
    }

    public void sendEnded(String helpeeId, String helperId, String roomId) {
        Map<String, Object> innerPayload = Map.of("roomId", roomId, "pointDelta", 10);
        Map<String, Object> payload = Map.of("type", "ENDED", "payload", innerPayload);
        messagingTemplate.convertAndSend((String) ("/topic/session/" + helpeeId), (Object) payload);
        messagingTemplate.convertAndSend((String) ("/topic/session/" + helperId), (Object) payload);
    }

    public void sendPeerLeft(String targetId, String roomId) {
        Map<String, Object> innerPayload = Map.of("roomId", roomId);
        Map<String, Object> payload = Map.of("type", "PEER_LEFT", "payload", innerPayload);
        messagingTemplate.convertAndSend((String) ("/topic/session/" + targetId), (Object) payload);
    }
}
