package com.mesh.hello.domain.communication.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class CallSession {
    private String roomId;
    private String helpeeId;
    private String helperId;
    private String helpeeToken;
    private String helperToken;
    private Instant startedAt;
}
