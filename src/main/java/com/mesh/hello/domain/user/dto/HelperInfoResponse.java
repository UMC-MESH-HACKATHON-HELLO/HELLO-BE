package com.mesh.hello.domain.user.dto;

/**
 * 도우미 본인 정보 응답(도우미 전용 엔드포인트용).
 */
public record HelperInfoResponse(Long userId, String username, String nickname, long points) {
}
