package com.mesh.hello.domain.fcm.dto;

public record TokenRequest(
        Long userId,  // 토큰 기반 인증 완료되면 삭제
        String token
) {}
