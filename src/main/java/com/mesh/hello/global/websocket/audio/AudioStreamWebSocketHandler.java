package com.mesh.hello.global.websocket.audio;

import com.mesh.hello.domain.calling.application.TranscribeStreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioStreamWebSocketHandler extends BinaryWebSocketHandler {

    private final TranscribeStreamingService transcribeStreamingService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = extractRoomId(session);
        if (roomId == null) {
            log.warn("[AudioWS] roomId 없음 — 연결 거부 sessionId={}", session.getId());
            try { session.close(CloseStatus.BAD_DATA); } catch (Exception ignored) {}
            return;
        }
        session.getAttributes().put("roomId", roomId);
        transcribeStreamingService.initStream(roomId);
        log.info("[AudioWS] 연결 roomId={}", roomId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String roomId = (String) session.getAttributes().get("roomId");
        if (roomId == null) return;

        var payload = message.getPayload();
        byte[] audio = new byte[payload.remaining()];
        payload.get(audio);
        transcribeStreamingService.pushAudio(roomId, audio);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get("roomId");
        if (roomId != null) {
            transcribeStreamingService.closeStream(roomId);
            log.info("[AudioWS] 연결 종료 roomId={} status={}", roomId, status);
        }
    }

    private String extractRoomId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "roomId".equals(kv[0])) return kv[1];
        }
        return null;
    }
}