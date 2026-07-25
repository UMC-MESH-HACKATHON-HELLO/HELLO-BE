package com.mesh.hello.domain.reward.domain;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 포인트 적립/차감 내역 한 건.
 *
 * <p>인메모리 저장소({@code InMemoryPointHistoryRepository})에서 관리되는 값 객체다.
 * {@code amount}는 적립이면 양수, 차감이면 음수를 가질 수 있다.</p>
 */
@Getter
public class PointHistory {

    private final Long historyId;
    private final Long userId;
    private final long amount;
    private final String reason;
    private final String roomId;
    private final LocalDateTime createdAt;

    public PointHistory(Long historyId, Long userId, long amount, String reason, String roomId,
                        LocalDateTime createdAt) {
        this.historyId = historyId;
        this.userId = userId;
        this.amount = amount;
        this.reason = reason;
        this.roomId = roomId;
        this.createdAt = createdAt;
    }
}
