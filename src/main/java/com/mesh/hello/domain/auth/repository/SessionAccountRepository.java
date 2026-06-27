package com.mesh.hello.domain.auth.repository;

import java.util.Optional;

/**
 * 익명 sessionId ↔ 로그인 userId 바인딩 저장소.
 *
 * <p>익명 세션(CM102) 자체는 재구현하지 않는다. 여기서는 "이 익명 sessionId가 어떤 계정으로
 * 로그인했는가"만 기록해, 매칭·포인트 시스템이 sessionId로 계정을 resolve할 수 있게 한다.</p>
 */
public interface SessionAccountRepository {

    void bind(String sessionId, Long userId);

    Optional<Long> findUserId(String sessionId);

    void unbind(String sessionId);
}
