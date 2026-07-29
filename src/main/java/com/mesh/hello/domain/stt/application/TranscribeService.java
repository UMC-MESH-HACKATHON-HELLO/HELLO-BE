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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

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

    // roomId → 발화 단위 원문 누적 (화자 구분 포함). helpee/helper 세션이 동시에 append할 수 있어 락 없는 동시성 안전 큐를 사용한다.
    private final ConcurrentHashMap<String, Queue<String>> transcripts = new ConcurrentHashMap<>();

    // roomId → {sessionId → 역할("helpee"/"helper")}
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> roomRoles = new ConcurrentHashMap<>();

    public void startSession(String sessionId, String roomId, String role) {
        // 소켓을 열기 전에 자리를 먼저 원자적으로 예약해서 동시 시작을 막는다.
        SttSession reserved = new SttSession(roomId, null);
        if (sessions.putIfAbsent(sessionId, reserved) != null) {
            log.warn("STT 세션 이미 존재: {}", sessionId);
            return;
        }

        transcripts.putIfAbsent(roomId, new ConcurrentLinkedQueue<>());
        roomRoles.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(sessionId, role);

        Request request = new Request.Builder()
                .url(RTZR_STREAMING_URL)
                .header("Authorization", "Bearer " + tokenProvider.getAccessToken())
                .build();

        AtomicReference<SttSession> sessionRef = new AtomicReference<>(reserved);

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
                // 그 사이 다른 세션으로 교체됐다면 지우지 않는다 (조건부 제거).
                sessions.remove(sessionId, sessionRef.get());
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("STT 완료: {} (code={}, reason={})", sessionId, code, reason);
            }
        });

        SttSession session = new SttSession(roomId, webSocket);
        sessionRef.set(session);
        sessions.put(sessionId, session);

        log.info("STT 세션 시작: {} (room: {}, role: {})", sessionId, roomId, role);
    }

    private void handleTranscript(String sessionId, String roomId, String json) {
        RtzrTranscriptResponse result;
        try {
            result = objectMapper.readValue(json, RtzrTranscriptResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("STT 응답 파싱 실패: {}", sessionId, e);
            return;
        }

        if (result.alternatives() == null || result.alternatives().isEmpty()) return;
        String text = result.alternatives().get(0).text();
        boolean isFinal = result.isFinal();
        if (text == null || text.isBlank()) return;

        if (isFinal) {
            String speakerRole = roomRoles.getOrDefault(roomId, new ConcurrentHashMap<>())
                    .getOrDefault(sessionId, "unknown");
            Queue<String> transcript = transcripts.get(roomId);
            if (transcript != null) {
                transcript.add("[" + speakerRole + "] " + text);
            }
        }

        TranscriptMessage msg = new TranscriptMessage(sessionId, text, isFinal);
        messagingTemplate.convertAndSend("/api/v1/topic/transcript/" + roomId, msg);
    }

    public void sendAudio(String sessionId, byte[] pcmData) {
        SttSession session = sessions.get(sessionId);
        if (session == null || session.webSocket() == null) return;
        session.webSocket().send(ByteString.of(pcmData));
    }

    public void stopSession(String sessionId) {
        SttSession session = sessions.remove(sessionId);
        if (session == null || session.webSocket() == null) return;
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

        Queue<String> lines = transcripts.remove(roomId);
        return lines != null ? String.join("\n", lines) : "";
    }

    private record SttSession(String roomId, WebSocket webSocket) {
    }
}
