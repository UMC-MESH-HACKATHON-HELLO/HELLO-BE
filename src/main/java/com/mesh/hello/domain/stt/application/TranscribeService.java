package com.mesh.hello.domain.stt.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesh.hello.domain.stt.dto.RtzrTranscriptResponse;
import com.mesh.hello.domain.stt.dto.TranscriptMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeService {

    private static final String RTZR_STREAMING_URL = "wss://openapi.vito.ai/v1/transcribe:streaming"
            + "?sample_rate=16000&encoding=LINEAR16"
            + "&use_itn=true&use_disfluency_filter=true&use_profanity_filter=false";

    private final OkHttpClient rtzrStreamingHttpClient;
    private final RtzrTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpMessagingTemplate messagingTemplate;

    // sessionId → STT 세션
    private final ConcurrentHashMap<String, SttSession> sessions = new ConcurrentHashMap<>();

    // roomId → 전체 원문 누적 (화자 구분 포함)
    private final ConcurrentHashMap<String, StringBuilder> transcripts = new ConcurrentHashMap<>();

    // roomId → {sessionId → 역할("helpee"/"helper")}
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> roomRoles = new ConcurrentHashMap<>();

    public void startSession(String sessionId, String roomId, String role) {
        if (sessions.containsKey(sessionId)) {
            log.warn("STT 세션 이미 존재: {}", sessionId);
            return;
        }

        transcripts.putIfAbsent(roomId, new StringBuilder());
        roomRoles.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(sessionId, role);

        Request request = new Request.Builder()
                .url(RTZR_STREAMING_URL)
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .build();

        WebSocket webSocket = rtzrStreamingHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("STT 세션 연결됨: {}", sessionId);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleTranscript(sessionId, roomId, text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("STT 오류: {} (status={})", sessionId, response != null ? response.code() : null, t);
                sessions.remove(sessionId);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("STT 완료: {} (code={}, reason={})", sessionId, code, reason);
            }
        });

        sessions.put(sessionId, new SttSession(roomId, webSocket));

        log.info("STT 세션 시작: {} (room: {}, role: {})", sessionId, roomId, role);
    }

    private void handleTranscript(String sessionId, String roomId, String json) {
        RtzrTranscriptResponse result;
        try {
            result = objectMapper.readValue(json, RtzrTranscriptResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("STT 응답 파싱 실패: {} ({})", sessionId, json, e);
            return;
        }

        if (result.alternatives() == null || result.alternatives().isEmpty()) return;
        String text = result.alternatives().get(0).text();
        boolean isFinal = result.isFinal();
        if (text == null || text.isBlank()) return;

        if (isFinal) {
            String speakerRole = roomRoles.getOrDefault(roomId, new ConcurrentHashMap<>())
                    .getOrDefault(sessionId, "unknown");
            StringBuilder transcript = transcripts.get(roomId);
            if (transcript != null) {
                transcript.append("[").append(speakerRole).append("] ")
                        .append(text).append("\n");
            }
        }

        TranscriptMessage msg = new TranscriptMessage(sessionId, text, isFinal);
        messagingTemplate.convertAndSend("/topic/transcript/" + roomId, msg);
    }

    public void sendAudio(String sessionId, byte[] pcmData) {
        SttSession session = sessions.get(sessionId);
        if (session == null) return;
        session.webSocket().send(ByteString.of(pcmData));
    }

    public void stopSession(String sessionId) {
        SttSession session = sessions.remove(sessionId);
        if (session == null) return;
        session.webSocket().send("EOS");
        log.info("STT 세션 종료 요청: {}", sessionId);
    }

    /** 방에 속한 모든 STT 세션을 종료하고 누적 텍스트를 반환한다. */
    public String flushTranscript(String roomId) {
        sessions.entrySet().stream()
                .filter(e -> roomId.equals(e.getValue().roomId()))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::stopSession);

        roomRoles.remove(roomId);

        StringBuilder sb = transcripts.remove(roomId);
        return sb != null ? sb.toString().trim() : "";
    }

    private record SttSession(String roomId, WebSocket webSocket) {
    }
}
