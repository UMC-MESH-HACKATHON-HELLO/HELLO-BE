package com.mesh.hello.domain.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도우미(도와주는 사람) 계정.
 *
 * <p>어르신은 로그인하지 않으므로 이 엔티티는 도우미 전용이다.
 * {@code password}는 반드시 BCrypt 해시로 저장한다(평문 금지).
 * 테이블명 {@code users} — 일부 DB에서 {@code user}가 예약어이기 때문.</p>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt 해시 문자열. */
    @Column(nullable = false)
    private String password;

    @Column
    private String nickname;

    /** 보유 포인트 총합. 실제 적립/차감 내역은 {@code PointHistory}(DB)로 관리한다. */
    @Column(nullable = false)
    private long points = 0L;

    @Builder
    public User(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.points = 0L;
    }

}
