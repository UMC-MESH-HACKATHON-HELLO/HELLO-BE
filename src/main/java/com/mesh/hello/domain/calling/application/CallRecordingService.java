package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallRecord;
import com.mesh.hello.domain.calling.repository.CallRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class CallRecordingService {

    private final CallRecordRepository callRecordRepository;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    /**
     * 신고 시 클라이언트가 녹음 파일을 업로드한다.
     */
    @Transactional
    public void reportWithRecording(String roomId, String reporterSessionId, MultipartFile audioFile) {
        String s3Key = "recordings/" + roomId + "/" + reporterSessionId + ".ogg";

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType(audioFile.getContentType())
                            .build(),
                    RequestBody.fromInputStream(audioFile.getInputStream(), audioFile.getSize())
            );
        } catch (IOException e) {
            throw new RuntimeException("녹음 파일 업로드 실패", e);
        }

        callRecordRepository.save(new CallRecord(roomId, reporterSessionId, s3Key));
    }
}
