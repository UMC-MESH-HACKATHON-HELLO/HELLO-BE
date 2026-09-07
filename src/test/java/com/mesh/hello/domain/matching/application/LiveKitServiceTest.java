package com.mesh.hello.domain.matching.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class LiveKitServiceTest {

    private LiveKitService liveKitService;

    @BeforeEach
    void setUp() {
        liveKitService = new LiveKitService();
        ReflectionTestUtils.setField(liveKitService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(liveKitService, "apiSecret", "test-secret-must-be-at-least-32chars!");
    }

    @Test
    @DisplayName("토큰은 JWT 형식(헤더.페이로드.서명 3파트)이어야 한다")
    void tokenIsJwtFormat() throws Exception {
        String token = liveKitService.createToken("room-abc", "user-123", LiveKitService.Role.HELPEE);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("토큰 페이로드에 roomId와 참가자 identity가 포함되어야 한다")
    void tokenPayloadContainsRoomAndIdentity() throws Exception {
        String token = liveKitService.createToken("room-abc", "user-123", LiveKitService.Role.HELPEE);

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(payload).contains("room-abc");
        assertThat(payload).contains("user-123");
    }

    @Test
    @DisplayName("같은 roomId라도 참가자가 다르면 서로 다른 토큰이 발급되어야 한다")
    void differentParticipantsGetDifferentTokens() throws Exception {
        String tokenA = liveKitService.createToken("room-abc", "user-A", LiveKitService.Role.HELPEE);
        String tokenB = liveKitService.createToken("room-abc", "user-B", LiveKitService.Role.HELPER);

        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    @Test
    @DisplayName("helpee 토큰은 카메라/화면공유/마이크를 모두 publish할 수 있다")
    void helpeeTokenCanPublishCameraAndScreenShare() throws Exception {
        String token = liveKitService.createToken("room-abc", "helpee-1", LiveKitService.Role.HELPEE);

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(payload).contains("\"canPublish\":true");
        assertThat(payload).contains("camera");
        assertThat(payload).contains("screen_share");
        assertThat(payload).contains("microphone");
    }

    @Test
    @DisplayName("helper 토큰은 마이크만 publish할 수 있고 카메라/화면공유는 publish할 수 없다")
    void helperTokenCanOnlyPublishMicrophone() throws Exception {
        String token = liveKitService.createToken("room-abc", "helper-1", LiveKitService.Role.HELPER);

        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(payload).contains("microphone");
        assertThat(payload).doesNotContain("camera");
        assertThat(payload).doesNotContain("screen_share");
    }
}