package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.Report;
import com.mesh.hello.domain.calling.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final S3Service s3Service;
    private final ReportTranscribeService reportTranscribeService;

    public void report(String roomId, String reason, MultipartFile audioFile, String sessionId) {
        String s3Key = "reports/" + roomId + "/" + sessionId + "/" + System.currentTimeMillis() + ".ogg";
        s3Service.upload(audioFile, s3Key);

        reportRepository.save(Report.builder()
                .roomId(roomId)
                .reason(reason)
                .s3Key(s3Key)
                .reporterSessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("[Report] 신고 접수 roomId={} session={}", roomId, sessionId);
        reportTranscribeService.startReportTranscribe(roomId, s3Key);
    }
}