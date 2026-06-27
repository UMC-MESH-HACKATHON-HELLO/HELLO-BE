package com.mesh.hello.domain.user.dto;

import java.time.LocalDateTime;

public class SessionResDTO {
    public record GetSession(
            String sessionId,
            LocalDateTime createdAt
    ) {}
}
