package com.mesh.hello.domain.reward.repository;

import com.mesh.hello.domain.reward.domain.PointHistory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPointHistoryRepository implements PointHistoryRepository {

    private final Map<Long, PointHistory> histories = new ConcurrentHashMap<>();
    private final AtomicLong historyIdGenerator = new AtomicLong(0);

    @Override
    public PointHistory save(Long userId, long amount, String reason, String roomId) {
        Long historyId = historyIdGenerator.getAndIncrement();
        PointHistory history = new PointHistory(historyId, userId, amount, reason, roomId, LocalDateTime.now());
        histories.put(historyId, history);
        return history;
    }

    @Override
    public List<PointHistory> findAllByUserId(Long userId) {
        return histories.values().stream()
                .filter(history -> history.getUserId().equals(userId))
                .toList();
    }
}
