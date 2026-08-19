package com.mesh.hello.domain.stt.application;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RTZR의 실제 스트리밍 WebSocket 연결 없이, {@code startSession}이 등록하는
 * {@link WebSocketListener}를 캡처해 {@code onMessage} 콜백(=STT 응답 수신)을 직접 흉내낸다.
 */
@ExtendWith(MockitoExtension.class)
class TranscribeServiceTest {

    @Mock
    private OkHttpClient rtzrStreamingHttpClient;

    @Mock
    private RtzrTokenProvider tokenProvider;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ForbiddenWordService forbiddenWordService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private WebSocket webSocket;

    private TranscribeService transcribeService;
    private WebSocketListener listener;

    @BeforeEach
    void setUp() {
        transcribeService = new TranscribeService(
                rtzrStreamingHttpClient, tokenProvider, messagingTemplate, forbiddenWordService, eventPublisher);

        given(tokenProvider.getAccessToken()).willReturn("token");

        ArgumentCaptor<WebSocketListener> listenerCaptor = ArgumentCaptor.forClass(WebSocketListener.class);
        given(rtzrStreamingHttpClient.newWebSocket(any(Request.class), listenerCaptor.capture()))
                .willReturn(webSocket);

        transcribeService.startSession("session-1", "room-1", "helpee");
        listener = listenerCaptor.getValue();
    }

    private String transcriptJson(String text, boolean isFinal) {
        return """
                {"seq":1,"start_at":0,"duration":0,"final":%s,"alternatives":[{"text":"%s","confidence":1.0}]}
                """.formatted(isFinal, text);
    }

    @Nested
    @DisplayName("금지어 감지 - 중간 인식 단계(interim)부터 검사")
    class ForbiddenWordDetectionTest {

        @Test
        @DisplayName("아직 확정(final)되지 않은 중간 인식 결과에서도 금지어가 감지되면 즉시 이벤트를 발행한다")
        void publishesEventOnInterimHit() {
            given(forbiddenWordService.findHit("아 씨발")).willReturn(Optional.of("씨발"));

            listener.onMessage(webSocket, transcriptJson("아 씨발", false));

            ArgumentCaptor<ForbiddenWordDetectedEvent> captor =
                    ArgumentCaptor.forClass(ForbiddenWordDetectedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            ForbiddenWordDetectedEvent event = captor.getValue();
            assertThat(event.roomId()).isEqualTo("room-1");
            assertThat(event.sessionId()).isEqualTo("session-1");
            assertThat(event.role()).isEqualTo("helpee");
            assertThat(event.matchedWord()).isEqualTo("씨발");
            assertThat(event.utterance()).isEqualTo("아 씨발");
        }

        @Test
        @DisplayName("금지어가 없는 텍스트는 이벤트를 발행하지 않는다")
        void doesNotPublishEventWhenNoHit() {
            given(forbiddenWordService.findHit("안녕하세요")).willReturn(Optional.empty());

            listener.onMessage(webSocket, transcriptJson("안녕하세요", false));

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("final 여부와 무관하게 금지어가 감지되면 이벤트를 발행한다")
        void publishesEventRegardlessOfFinalFlag() {
            given(forbiddenWordService.findHit("병신아")).willReturn(Optional.of("병신아"));

            listener.onMessage(webSocket, transcriptJson("병신아", true));

            verify(eventPublisher).publishEvent(any(ForbiddenWordDetectedEvent.class));
        }
    }
}
