package com.mesh.hello.domain.matching.application;

import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LiveKitService {
    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    public String createToken(String roomId, String sessionId) throws Exception {
        AccessToken token = new AccessToken(apiKey, apiSecret);
        token.setName(sessionId);
        token.setIdentity(sessionId);
        token.addGrants(new RoomJoin(true), new RoomName(roomId));
        return token.toJwt();
    }
}
