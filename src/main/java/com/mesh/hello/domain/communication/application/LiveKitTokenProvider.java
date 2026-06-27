package com.mesh.hello.domain.communication.application;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LiveKitTokenProvider {

    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    public String createToken(String roomId, String participantId) {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setIdentity(participantId);
        token.addGrants(new RoomJoin(true), new RoomName(roomId));
        return token.toJwt();
    }
}