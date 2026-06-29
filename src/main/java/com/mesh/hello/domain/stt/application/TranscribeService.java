package com.mesh.hello.domain.stt.application;

import com.mesh.hello.domain.stt.dto.TranscriptMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.*;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeService {

    private final TranscribeStreamingAsyncClient transcribeClient;
    private final SimpMessagingTemplate messagingTemplate;

    // sessionId → STT 세션
    private final ConcurrentHashMap<String, SttSession> sessions = new ConcurrentHashMap<>();

    public void startSession(String sessionId, String roomId) {
        if (sessions.containsKey(sessionId)) {
            log.warn("STT 세션 이미 존재: {}", sessionId);
            return;
        }

        AudioStreamPublisher publisher = new AudioStreamPublisher();
        SttSession session = new SttSession(roomId, publisher);
        sessions.put(sessionId, session);

        StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                .languageCode(LanguageCode.KO_KR)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(16000)
                .build();

        StartStreamTranscriptionResponseHandler handler = StartStreamTranscriptionResponseHandler.builder()
                .onEventStream(eventStream -> eventStream.subscribe(event -> {
                    if (event instanceof TranscriptEvent transcriptEvent) {
                        transcriptEvent.transcript().results().forEach(result -> {
                            if (result.alternatives().isEmpty()) return;
                            String text = result.alternatives().get(0).transcript();
                            boolean isFinal = !result.isPartial();
                            if (text == null || text.isBlank()) return;

                            TranscriptMessage msg = new TranscriptMessage(sessionId, text, isFinal);
                            messagingTemplate.convertAndSend("/topic/transcript/" + roomId, msg);
                        });
                    }
                }))
                .onComplete(() -> log.info("STT 완료: {}", sessionId))
                .onError(e -> log.error("STT 오류: {}", sessionId, e))
                .build();

        transcribeClient.startStreamTranscription(request, publisher, handler)
                .exceptionally(e -> {
                    log.error("STT 세션 시작 실패: {}", sessionId, e);
                    sessions.remove(sessionId);
                    return null;
                });

        log.info("STT 세션 시작: {} (room: {})", sessionId, roomId);
    }

    public void sendAudio(String sessionId, byte[] pcmData) {
        SttSession session = sessions.get(sessionId);
        if (session == null) return;

        AudioEvent event = AudioEvent.builder()
                .audioChunk(SdkBytes.fromByteBuffer(ByteBuffer.wrap(pcmData)))
                .build();
        session.publisher().enqueue(event);
    }

    public void stopSession(String sessionId) {
        SttSession session = sessions.remove(sessionId);
        if (session == null) return;
        session.publisher().complete();
        log.info("STT 세션 종료: {}", sessionId);
    }

    // ── 내부 타입 ──────────────────────────────────────────

    private record SttSession(String roomId, AudioStreamPublisher publisher) {}

    static class AudioStreamPublisher implements Publisher<AudioStream> {

        private final LinkedBlockingQueue<AudioStream> queue = new LinkedBlockingQueue<>();
        private Subscriber<? super AudioStream> subscriber;
        private volatile boolean completed = false;

        void enqueue(AudioEvent event) {
            queue.offer(event);
            drain();
        }

        void complete() {
            completed = true;
            drain();
        }

        private synchronized void drain() {
            if (subscriber == null) return;
            AudioStream item;
            while ((item = queue.poll()) != null) {
                subscriber.onNext(item);
            }
            if (completed && queue.isEmpty()) {
                subscriber.onComplete();
            }
        }

        @Override
        public void subscribe(Subscriber<? super AudioStream> s) {
            this.subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override public void request(long n) { drain(); }
                @Override public void cancel() { sessions_noop(); }
            });
        }

        private void sessions_noop() {}
    }
}