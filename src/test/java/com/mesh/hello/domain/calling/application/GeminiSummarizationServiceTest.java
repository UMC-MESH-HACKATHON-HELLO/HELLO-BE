package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeminiSummarizationServiceTest {

    @Mock
    private RestClient geminiRestClient;

    @Mock
    private CallSummaryRepository callSummaryRepository;

    @Mock
    private CallSummaryPersistenceService persistenceService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GeminiSummarizationService service;

    private CallSummary pending;

    @BeforeEach
    void setUp() {
        service = new GeminiSummarizationService(
                geminiRestClient, callSummaryRepository, persistenceService, messagingTemplate);

        pending = new CallSummary("room-1", "helpee-1", "helper-1", 120);
        given(callSummaryRepository.findTopByRoomIdOrderByIdDesc("room-1")).willReturn(Optional.of(pending));
    }

    @Test
    @DisplayName("summarizeAndNotify - transcript가 없으면 category ETC로 요약을 완료 처리한다")
    void summarizeAndNotify_blankTranscript_completesWithEtcCategory() {
        service.summarizeAndNotify("room-1", "helpee-1", "helper-1", "  ");

        verify(persistenceService).completeSummary(
                pending, null, "통화 내용이 없어 요약을 생성하지 않았습니다.", CallSummary.CallCategory.ETC);
    }
}