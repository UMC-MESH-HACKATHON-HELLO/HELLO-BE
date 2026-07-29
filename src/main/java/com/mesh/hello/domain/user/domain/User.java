package com.mesh.hello.domain.user.domain;

import com.mesh.hello.domain.user.enums.Provider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도우미(도와주는 사람) 계정.
 *
 * <p>어르신은 로그인하지 않으므로 이 엔티티는 도우미 전용이다.
 * {@code password}는 반드시 BCrypt 해시로 저장한다(평문 금지).
 * 카카오 로그인 유저는 랜덤 BCrypt 해시로 채운다.
 * 테이블명 {@code users} — 일부 DB에서 {@code user}가 예약어이기 때문.</p>
 *
 * <p>복합 유니크 제약 {@code uq_users_provider_provider_id}:
 * 동일한 소셜 계정이 중복 가입되지 않도록 (provider, provider_id) 쌍을 유일하게 강제한다.
 * LOCAL 유저는 provider_id가 null이므로 유니크 제약 대상에서 DB 수준에서 제외된다
 * (MySQL/MariaDB는 NULL 값 쌍을 중복으로 판단하지 않는다).</p>
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_users_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt 해시 문자열. 카카오 유저는 랜덤 BCrypt 해시로 채운다(평문 금지). */
    @Column(nullable = false)
    private String password;

    @Column
    private String nickname;

    /** 통화당 적립 포인트. 적립 로직은 이번 범위 아님(필드만 보유). */
    @Column(nullable = false)
    private long points = 0L;

    /**
     * 로그인 제공자. 기본값 {@code LOCAL}.
     * DB 컬럼 타입은 VARCHAR(enum 이름 저장).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider = Provider.LOCAL;

    /**
     * 소셜 제공자의 사용자 고유 ID(카카오 회원번호 등).
     * LOCAL 유저는 null.
     */
    @Column(name = "provider_id")
    private String providerId;

    /** LOCAL 회원가입용 빌더. provider = LOCAL, providerId = null. */
    @Builder
    public User(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
        this.points = 0L;
        this.provider = Provider.LOCAL;
        this.providerId = null;
    }

    /**
     * 소셜 로그인 회원가입용 팩토리 메서드.
     *
     * @param username   내부 식별자(예: "kakao_12345678")
     * @param password   랜덤 BCrypt 해시(소셜 유저는 비밀번호 로그인 불가)
     * @param nickname   소셜에서 가져온 닉네임
     * @param provider   OAuth 제공자
     * @param providerId OAuth 제공자의 사용자 ID
     */
    public static User ofSocial(String username, String password, String nickname,
                                Provider provider, String providerId) {
        User user = new User();
        user.username = username;
        user.password = password;
        user.nickname = nickname;
        user.points = 0L;
        user.provider = provider;
        user.providerId = providerId;
        return user;
    }
}
