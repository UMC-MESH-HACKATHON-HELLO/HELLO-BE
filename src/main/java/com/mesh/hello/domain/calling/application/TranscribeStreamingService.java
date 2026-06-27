package com.mesh.hello.domain.calling.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.*;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscribeStreamingService {

    private final TranscribeStreamingAsyncClient transcribeClient;
    private final TranscriptBufferStore bufferStore;

    private final ConcurrentHashMap<String, Sinks.Many<ByteBuffer>> audioSinks = new ConcurrentHashMap<>();
    private final ExecutorService streamExecutor =
            Executors.newCachedThreadPool(r -> new Thread(r, "transcribe-stream-"));

    public void initStream(String roomId) {
        Sinks.Many<ByteBuffer> sink = Sinks.many().unicast().onBackpressureBuffer();
        audioSinks.put(roomId, sink);
        streamExecutor.submit(() -> runStream(roomId, sink.asFlux()));
        log.info("[Transcribe] 스트림 초기화 roomId={}", roomId);
    }

    public void pushAudio(String roomId, byte[] audio) {
        Sinks.Many<ByteBuffer> sink = audioSinks.get(roomId);
        if (sink != null) {
            sink.tryEmitNext(ByteBuffer.wrap(audio));
        }
    }

    public void closeStream(String roomId) {
        Sinks.Many<ByteBuffer> sink = audioSinks.remove(roomId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("[Transcribe] 스트림 종료 roomId={}", roomId);
        }
    }

    private void runStream(String roomId, Flux<ByteBuffer> audioFlux) {
        StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                .languageCode(LanguageCode.KO_KR)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(16_000)
                .build();

        StartStreamTranscriptionResponseHandler handler =
                StartStreamTranscriptionResponseHandler.builder()
                        .onError(e -> log.error("[Transcribe] 스트림 오류 roomId={}: {}", roomId, e.getMessage()))
                        .subscriber(event -> {
                            if (event instanceof TranscriptEvent te) {
                                te.transcript().results().stream()
                                        .filter(r -> !r.isPartial())
                                        .flatMap(r -> r.alternatives().stream())
                                        .map(Alternative::transcript)
                                        .filter(t -> t != null && !t.isBlank())
                                        .forEach(t -> bufferStore.append(roomId, t));
                            }
                        })
                        .build();

        AudioStreamPublisher audioPublisher = subscriber ->
                Flux.<AudioStream>from(
                        audioFlux.map(buf ->
                                AudioEvent.builder()
                                        .audioChunk(SdkBytes.fromByteBuffer(buf))
                                        .build()
                        )
                ).subscribe(subscriber);

        try {
            transcribeClient.startStreamTranscription(request, audioPublisher, handler).join();
            log.info("[Transcribe] 스트림 완료 roomId={}", roomId);
        } catch (Exception e) {
            log.error("[Transcribe] 스트림 실패 roomId={}: {}", roomId, e.getMessage());
        }
    }
}