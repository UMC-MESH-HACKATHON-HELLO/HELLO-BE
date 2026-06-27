package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallSessionService {

    private final TranscriptBufferStore bufferStore;
    private final BedrockSummaryService bedrockService;
    private final CallSummaryRepository summaryRepository;

    @Async
    public void onCallEnded(String roomId) {
        String transcript = bufferStore.flushAndGet(roomId);

        if (transcript.isBlank()) {
            log.warn("[CallSession] transcript 없음 — 요약 생략 roomId={}", roomId);
            return;
        }

        try {
            String summary = bedrockService.summarize(transcript, false);
            summaryRepository.save(CallSummary.builder()
                    .roomId(roomId)
                    .summary(summary)
                    .summaryType(CallSummary.SummaryType.REALTIME)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("[CallSession] summary 저장 완료 roomId={}", roomId);
        } catch (Exception e) {
            log.error("[CallSession] 요약 실패 roomId={}: {}", roomId, e.getMessage());
        }
    }
}