package com.mesh.hello.domain.reward.dto;

import com.mesh.hello.domain.reward.domain.PointHistory;
import java.time.LocalDateTime;

public record PointHistoryItem(
        Long historyId,
        long amount,
        String reason,
        String roomId,
        LocalDateTime createdAt
) {
    public static PointHistoryItem from(PointHistory history) {
        return new PointHistoryItem(
                history.getHistoryId(),
                history.getAmount(),
                history.getReason(),
                history.getRoomId(),
                history.getCreatedAt()
        );
    }
}
