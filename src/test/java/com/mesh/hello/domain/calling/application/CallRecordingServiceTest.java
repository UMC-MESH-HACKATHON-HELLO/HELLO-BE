package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallRecord;
import com.mesh.hello.domain.calling.repository.CallRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CallRecordingServiceTest {

    @Mock
    private CallRecordRepository callRecordRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private CallRecordingService callRecordingService;

    @Test
    @DisplayName("reportWithRecording - S3에 업로드하고 DB에 레코드를 저장한다")
    void reportWithRecording_uploadsAndSaves() throws IOException {
        MultipartFile audioFile = mock(MultipartFile.class);
        given(audioFile.getInputStream()).willReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        given(audioFile.getSize()).willReturn(3L);
        given(audioFile.getContentType()).willReturn("audio/ogg");

        callRecordingService.reportWithRecording("room-1", "session-1", audioFile);

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        ArgumentCaptor<CallRecord> captor = ArgumentCaptor.forClass(CallRecord.class);
        verify(callRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getRoomId()).isEqualTo("room-1");
        assertThat(captor.getValue().getReporterSessionId()).isEqualTo("session-1");
        assertThat(captor.getValue().getS3Key()).isEqualTo("recordings/room-1/session-1.ogg");
    }
}
