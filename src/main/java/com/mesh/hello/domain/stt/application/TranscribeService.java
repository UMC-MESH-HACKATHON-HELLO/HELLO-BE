package com.mesh.hello.domain.stt.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesh.hello.domain.stt.dto.RtzrTranscriptResponse;
import com.mesh.hello.domain.stt.dto.TranscriptMessage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class TranscribeService {

    private static final String RTZR_STREAMING_URL = "wss://openapi.vito.ai/v1/transcribe:streaming"
            + "?sample_rate=16000&encoding=LINEAR16"
            + "&use_itn=true&use_disfluency_filter=true&use_profanity_filter=false";

    // EOS 전송 후 RTZR의 마지막 인식 결과(onClosed)를 기다리는 최대 시간
    private static final long FLUSH_TIMEOUT_SECONDS = 3;

    private final OkHttpClient rtzrStreamingHttpClient;
    private final RtzrTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpMessagingTemplate messagingTemplate;
    private final ForbiddenWordService forbiddenWordService;
    private final ApplicationEventPublisher eventPublisher;

    public TranscribeService(
            @Qualifier("rtzrStreamingHttpClient") OkHttpClient rtzrStreamingHttpClient,
            RtzrTokenProvider tokenProvider,
            SimpMessagingTemplate messagingTemplate,
            ForbiddenWordService forbiddenWordService,
            ApplicationEventPublisher eventPublisher) {
        this.rtzrStreamingHttpClient = rtzrStreamingHttpClient;
        this.tokenProvider = tokenProvider;
        this.messagingTemplate = messagingTemplate;
        this.forbiddenWordService = forbiddenWordService;
        this.eventPublisher = eventPublisher;
    }

    // sessionId → STT 세션
    private final ConcurrentHashMap<String, SttSession> sessions = new ConcurrentHashMap<>();

    // roomId → 발화 단위 원문 누적 (화자 구분 포함). helpee/helper 세션이 동시에 append할 수 있어 락 없는 동시성 안전 큐를 사용한다.
    private final ConcurrentHashMap<String, Queue<String>> transcripts = new ConcurrentHashMap<>();

    // roomId → {sessionId → 역할("helpee"/"helper")}
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> roomRoles = new ConcurrentHashMap<>();

    public void startSession(String sessionId, String roomId, String role) {
        // 소켓을 열기 전에 자리를 먼저 원자적으로 예약해서 동시 시작을 막는다.
        SttSession reserved = new SttSession(roomId, null, null);
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
        CompletableFuture<Void> completed = new CompletableFuture<>();

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
                completed.complete(null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                log.info("STT 완료: {} (code={}, reason={})", sessionId, code, reason);
                completed.complete(null);
            }
        });

        SttSession session = new SttSession(roomId, webSocket, completed);
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

        ConcurrentHashMap<String, String> roles = roomRoles.get(roomId);
        String speakerRole = roles != null ? roles.getOrDefault(sessionId, "unknown") : "unknown";

        if (isFinal) {
            Queue<String> transcript = transcripts.get(roomId);
            if (transcript != null) {
                transcript.add("[" + speakerRole + "] " + text);
            }
        }

        // 강제 종료가 핵심 기능이므로 문장이 확정(isFinal)되길 기다리지 않고 중간 인식 단계에서 검사한다.
        forbiddenWordService.findHit(text).ifPresent(matchedWord -> {
            log.warn("금지어 감지: room={}, session={}, word={}", roomId, sessionId, matchedWord);
            eventPublisher.publishEvent(
                    new ForbiddenWordDetectedEvent(roomId, sessionId, speakerRole, matchedWord, text));
        });

        TranscriptMessage msg = new TranscriptMessage(sessionId, text, isFinal);
        messagingTemplate.convertAndSend("/api/v1/topic/transcript/" + roomId, msg);
    }

    public void sendAudio(String sessionId, byte[] pcmData) {
        SttSession session = sessions.get(sessionId);
        if (session == null || session.webSocket() == null) return;
        session.webSocket().send(ByteString.of(pcmData));
    }

    public CompletableFuture<Void> stopSession(String sessionId) {
        SttSession session = sessions.remove(sessionId);
        if (session == null || session.webSocket() == null) return CompletableFuture.completedFuture(null);
        session.webSocket().send("EOS");
        log.info("STT 세션 종료 요청: {}", sessionId);
        return session.completed();
    }

    /** 방에 속한 모든 STT 세션을 종료하고, RTZR이 마지막 인식 결과를 보낼 때까지 잠시 기다린 뒤 누적 텍스트를 반환한다. */
    public String flushTranscript(String roomId) {
        List<CompletableFuture<Void>> completions = sessions.entrySet().stream()
                .filter(e -> roomId.equals(e.getValue().roomId()))
                .map(Map.Entry::getKey)
                .toList()
                .stream()
                .map(this::stopSession)
                .toList();

        try {
            CompletableFuture.allOf(completions.toArray(new CompletableFuture[0]))
                    .get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("STT 세션 종료 대기 타임아웃 (room: {})", roomId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            log.warn("STT 세션 종료 대기 중 오류 (room: {})", roomId, e);
        }

        roomRoles.remove(roomId);

        Queue<String> lines = transcripts.remove(roomId);
        return lines != null ? String.join("\n", lines) : "";
    }

    private record SttSession(String roomId, WebSocket webSocket, CompletableFuture<Void> completed) {
    }
}
