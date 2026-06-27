package com.mesh.hello.domain.matching.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignalMessage {
    private String type;
    private String sessionId;
    private String roomId;
    private Object payload;
}
