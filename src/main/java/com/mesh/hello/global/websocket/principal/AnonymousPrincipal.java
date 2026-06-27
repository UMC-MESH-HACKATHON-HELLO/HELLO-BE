package com.mesh.hello.global.websocket.principal;

import java.security.Principal;
import java.util.Objects;

/**
 * 무인증 익명 사용자를 식별하는 Principal.
 *
 * <p>회원/로그인이 없으므로 sessionId(UUID 문자열)가 사용자의 유일한 식별자다.
 * {@link #getName()}이 sessionId를 반환하는 것이 핵심인데,
 * Spring의 {@code /user/queue/...} 라우팅이 Principal 이름을 키로 1:1 큐를 잡기 때문이다.</p>
 */
public record AnonymousPrincipal(String sessionId) implements Principal {

    public AnonymousPrincipal {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }

    @Override
    public String getName() {
        return sessionId;
    }
}
