package com.mesh.hello.domain.reward.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포인트 적립/차감 내역 한 건.
 *
 * <p>{@code amount}는 적립이면 양수, 차감이면 음수를 가질 수 있다.</p>
 */
@Entity
@Table(name = "point_histories", indexes = @Index(name = "idx_point_histories_user_id", columnList = "userId"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String reason;

    @Column
    private String roomId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public PointHistory(Long userId, long amount, String reason, String roomId) {
        this.userId = userId;
        this.amount = amount;
        this.reason = reason;
        this.roomId = roomId;
        this.createdAt = LocalDateTime.now();
    }
}
