package com.mesh.hello.domain.matching.application;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LiveKitService {
    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    /**
     * 어르신(helpee)은 카메라/화면공유까지 publish할 수 있지만, 도우미(helper)는
     * 마이크만 publish할 수 있다 — 어르신 → 도우미 단방향 영상 스트리밍(VC101/SC101)을
     * 서버가 발급하는 토큰 권한 수준에서 강제하기 위함이다.
     */
    public enum Role {
        HELPEE(List.of("camera", "microphone", "screen_share")),
        HELPER(List.of("microphone"));

        private final List<String> publishableSources;

        Role(List<String> publishableSources) {
            this.publishableSources = publishableSources;
        }
    }

    public String createToken(String roomId, String sessionId, Role role) throws Exception {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setName(sessionId);
        token.setIdentity(sessionId);
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomId),
                new CanPublish(true),
                new CanPublishSources(role.publishableSources),
                new CanSubscribe(true)
        );
        return token.toJwt();
    }
}
