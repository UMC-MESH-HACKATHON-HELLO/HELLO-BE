package com.mesh.hello.domain.matching.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mesh.hello.domain.matching.application.MatchingService;
import com.mesh.hello.domain.matching.dto.SignalMessage;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * {@link SignalingController} 단위 테스트.
 *
 * <p>{@code /signal/{roomId}}가 발신자를 해당 방의 참가자로 검증한 뒤에만 중계하는지 확인한다.
 * 검증 없이는 임의의 roomId로 SDP/ICE를 주입해 남의 통화에 끼어들 수 있었다(이슈 #42).</p>
 */
@ExtendWith(MockitoExtension.class)
class SignalingControllerTest {

    @Mock
    private MatchingService matchingService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SignalingController controller;

    @Test
    @DisplayName("방 참가자의 signal은 검증 후 중계된다")
    void relaysSignalForParticipant() {
        controller = new SignalingController(matchingService, messagingTemplate);
        Principal principal = () -> "helpee-1";
        SignalMessage msg = new SignalMessage();
        msg.setRoomId("room-1");

        controller.signal(principal, "room-1", msg);

        verify(matchingService).assertParticipant("helpee-1", "room-1");
        verify(messagingTemplate).convertAndSend(eq("/api/v1/topic/room/room-1"), any(Object.class));
    }

    @Test
    @DisplayName("방 참가자가 아니면 중계하지 않고 예외를 던진다")
    void rejectsSignalForNonParticipant() {
        controller = new SignalingController(matchingService, messagingTemplate);
        Principal principal = () -> "stranger";
        SignalMessage msg = new SignalMessage();
        msg.setRoomId("room-1");
        doThrow(new BusinessException(ErrorCode.FORBIDDEN_SESSION))
                .when(matchingService).assertParticipant("stranger", "room-1");

        assertThatThrownBy(() -> controller.signal(principal, "room-1", msg))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN_SESSION);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}