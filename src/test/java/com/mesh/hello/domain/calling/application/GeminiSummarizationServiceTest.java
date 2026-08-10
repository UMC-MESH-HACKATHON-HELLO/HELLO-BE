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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GeminiSummarizationServiceTest {

    @Mock
    private RestClient geminiRestClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

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
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");

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

    @Test
    @DisplayName("summarizeAndNotify - Gemini가 정상 응답하면 매핑된 category로 요약을 완료 처리한다")
    void summarizeAndNotify_geminiRespondsSuccessfully_completesWithMappedCategory() {
        String transcript = "어르신: 지도 앱 켜는 법을 모르겠어요. 도우미: 홈 화면에서 지도 아이콘을 눌러드릴게요.";
        String geminiResponseBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "```json\\n{\\"requestedHelp\\": \\"지도 앱 사용법 안내\\", \\"providedHelp\\": \\"1. 홈 화면에서 지도 아이콘 누르기\\", \\"result\\": \\"해결\\", \\"category\\": \\"스마트폰\\"}\\n```"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        given(geminiRestClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString(), any(Object[].class))).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), any())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
        given(requestBodySpec.body(any(Object.class))).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(String.class)).willReturn(geminiResponseBody);

        service.summarizeAndNotify("room-1", "helpee-1", "helper-1", transcript);

        String expectedSummaryText = "요청: 지도 앱 사용법 안내\n제공된 도움: 1. 홈 화면에서 지도 아이콘 누르기\n결과: 해결";
        verify(persistenceService).completeSummary(
                pending, transcript, expectedSummaryText, CallSummary.CallCategory.SMARTPHONE);
    }
}