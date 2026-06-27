package com.mesh.hello.domain.calling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportTranscribeService {

    private final TranscribeClient transcribeClient;
    private final BedrockSummaryService bedrockService;
    private final CallSummaryRepository summaryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Async
    public void startReportTranscribe(String roomId, String s3Key) {
        String jobName = "report-" + roomId.replace("-", "") + "-" + System.currentTimeMillis();

        try {
            transcribeClient.startTranscriptionJob(
                    StartTranscriptionJobRequest.builder()
                            .transcriptionJobName(jobName)
                            .languageCode(LanguageCode.KO_KR)
                            .mediaFormat(MediaFormat.OGG)
                            .media(Media.builder()
                                    .mediaFileUri("s3://" + bucket + "/" + s3Key)
                                    .build())
                            .build()
            );

            log.info("[ReportTranscribe] Job 시작 roomId={} job={}", roomId, jobName);

            String transcript = pollUntilCompleted(jobName);
            if (transcript == null) return;

            String summary = bedrockService.summarize(transcript, true);

            summaryRepository.findByRoomId(roomId).ifPresentOrElse(
                    s -> {
                        s.updateWithReportSummary(summary);
                        summaryRepository.save(s);
                        log.info("[ReportTranscribe] summary 덮어쓰기 완료 roomId={}", roomId);
                    },
                    () -> {
                        summaryRepository.save(CallSummary.builder()
                                .roomId(roomId)
                                .summary(summary)
                                .summaryType(CallSummary.SummaryType.REPORT)
                                .createdAt(LocalDateTime.now())
                                .build());
                        log.info("[ReportTranscribe] summary 신규 저장 roomId={}", roomId);
                    }
            );

        } catch (Exception e) {
            log.error("[ReportTranscribe] 실패 roomId={}: {}", roomId, e.getMessage());
        }
    }

    private String pollUntilCompleted(String jobName) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Thread.sleep(30_000);

            GetTranscriptionJobResponse resp = transcribeClient.getTranscriptionJob(
                    GetTranscriptionJobRequest.builder().transcriptionJobName(jobName).build()
            );
            TranscriptionJobStatus status = resp.transcriptionJob().transcriptionJobStatus();

            if (status == TranscriptionJobStatus.COMPLETED) {
                String fileUri = resp.transcriptionJob().transcript().transcriptFileUri();
                return extractText(fileUri);
            }
            if (status == TranscriptionJobStatus.FAILED) {
                log.error("[ReportTranscribe] Job 실패 job={}", jobName);
                return null;
            }
        }
        log.warn("[ReportTranscribe] 타임아웃 job={}", jobName);
        return null;
    }

    private String extractText(String url) throws Exception {
        String json = restTemplate.getForObject(url, String.class);
        JsonNode root = objectMapper.readTree(json);
        return root.path("results").path("transcripts").get(0).path("transcript").asText();
    }
}