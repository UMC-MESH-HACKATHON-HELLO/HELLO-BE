package com.mesh.hello.domain.auth.repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

/**
 * 인메모리 바인딩 저장소(MVP). 단일 인스턴스 전제. 분산 환경에서는 Redis 등으로 교체.
 */
@Repository
public class InMemorySessionAccountRepository implements SessionAccountRepository {

    private final ConcurrentMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    @Override
    public void bind(String sessionId, Long userId) {
        sessionToUser.put(sessionId, userId);
    }

    @Override
    public Optional<Long> findUserId(String sessionId) {
        return Optional.ofNullable(sessionToUser.get(sessionId));
    }

    @Override
    public void unbind(String sessionId) {
        sessionToUser.remove(sessionId);
    }
}
