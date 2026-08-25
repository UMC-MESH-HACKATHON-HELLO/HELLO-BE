package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.repository.InMemoryMatchingRoomRepository;
import com.mesh.hello.domain.matching.repository.MatchingRoomRepository;
import com.mesh.hello.domain.reward.application.PointService;
import com.mesh.hello.domain.stt.application.TranscribeService;
import com.mesh.hello.global.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private LiveKitService liveKitService;
    @Mock private TranscribeService transcribeService;
    @Mock private GeminiSummarizationService geminiSummarizationService;
    @Mock private SessionAccountRepository sessionAccountRepository;
    @Mock private PointService pointService;
    @Mock private PriorityMatchingService priorityMatchingService;

    private MatchingRoomRepository matchingRoomRepository;
    private MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingRoomRepository = new InMemoryMatchingRoomRepository();
        matchingService = new MatchingService(
                matchingRoomRepository,
                messagingTemplate,
                liveKitService,
                transcribeService,
                geminiSummarizationService,
                sessionAccountRepository,
                pointService,
                priorityMatchingService
        );
    }

    @Test
    @DisplayName("대기 helper가 없으면 우선순위 큐에 helpee를 등록하고 대기 알림을 보낸다")
    void requestMatch_waitsWhenNoHelperExists() {
        given(priorityMatchingService.matchHelper(
                "helpee-1", CallSummary.CallCategory.KIOSK
        )).willReturn(Optional.empty());

        matchingService.requestMatch("helpee-1", CallSummary.CallCategory.KIOSK);

        verify(priorityMatchingService).matchHelper("helpee-1", CallSummary.CallCategory.KIOSK);
        assertQueueSignal("helpee-1", "NO_HELPER");
    }

    @Test
    @DisplayName("우선순위 큐에서 선택된 helper와 방을 만들고 양측에 토큰을 보낸다")
    void requestMatch_matchesSelectedHelper() throws Exception {
        given(priorityMatchingService.matchHelper(anyString(), eq(CallSummary.CallCategory.SMARTPHONE)))
                .willReturn(Optional.of("helper-1"));
        given(liveKitService.createToken(anyString(), eq("helpee-1"))).willReturn("helpee-token");
        given(liveKitService.createToken(anyString(), eq("helper-1"))).willReturn("helper-token");

        matchingService.requestMatch("helpee-1", CallSummary.CallCategory.SMARTPHONE);

        assertThat(matchingRoomRepository.findBySessionId("helpee-1")).isPresent();
        assertThat(matchingRoomRepository.findBySessionId("helper-1")).isPresent();
    }

    @Test
    @DisplayName("방 생성 실패 시 선택된 helper를 우선순위 큐에 복구한다")
    void requestMatch_restoresHelperWhenTokenCreationFails() throws Exception {
        given(priorityMatchingService.matchHelper(anyString(), eq(CallSummary.CallCategory.ROAD_GUIDE)))
                .willReturn(Optional.of("helper-1"));
        given(liveKitService.createToken(anyString(), anyString()))
                .willThrow(new RuntimeException("LiveKit error"));

        matchingService.requestMatch("helpee-1", CallSummary.CallCategory.ROAD_GUIDE);

        verify(priorityMatchingService).restoreHelper("helper-1");
        assertQueueSignal("helpee-1", "NO_HELPER");
    }

    @Test
    @DisplayName("대기 helpee가 없으면 helper가 우선순위 큐에 등록된 상태로 대기 알림을 받는다")
    void registerHelper_waitsWhenNoHelpeeExists() {
        given(priorityMatchingService.registerHelper("helper-1")).willReturn(Optional.empty());

        matchingService.registerHelper("helper-1");

        assertQueueSignal("helper-1", "WAITING");
    }

    @Test
    @DisplayName("방 생성 실패 시 helpee와 helper를 원래 카테고리로 복구한다")
    void registerHelper_restoresBothParticipantsWhenTokenCreationFails() throws Exception {
        PriorityMatchingService.MatchedHelpee helpee = new PriorityMatchingService.MatchedHelpee(
                "helpee-1", CallSummary.CallCategory.KIOSK
        );
        given(priorityMatchingService.registerHelper("helper-1")).willReturn(Optional.of(helpee));
        given(liveKitService.createToken(anyString(), anyString()))
                .willThrow(new RuntimeException("LiveKit error"));

        matchingService.registerHelper("helper-1");

        verify(priorityMatchingService).restoreHelpee("helpee-1", CallSummary.CallCategory.KIOSK);
        verify(priorityMatchingService).restoreHelper("helper-1");
    }

    @SuppressWarnings("unchecked")
    private void assertQueueSignal(String sessionId, String type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/api/v1/queue/signal/" + sessionId), captor.capture()
        );
        Map<String, String> result = (Map<String, String>) ((ApiResponse<?>) captor.getValue()).getResult();
        assertThat(result).containsEntry("type", type);
    }
}
