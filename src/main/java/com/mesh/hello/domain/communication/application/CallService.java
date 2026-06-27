package com.mesh.hello.domain.communication.application;

import com.mesh.hello.domain.communication.domain.CallSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CallService {

    private final LiveKitTokenProvider tokenProvider;

    public CallSession createCall(String helpeeId, String helperId) {
        String roomId = "room-" + UUID.randomUUID().toString().substring(0, 8);
        return new CallSession(
                roomId, helpeeId, helperId,
                tokenProvider.createToken(roomId, helpeeId),
                tokenProvider.createToken(roomId, helperId),
                Instant.now()
        );
    }
}
