package com.mesh.hello.domain.user.domain;

import com.mesh.hello.domain.user.enums.Provider;
import com.mesh.hello.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
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
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_users_username",
                        columnNames = {"username"}
                ),
                @UniqueConstraint(
                        name = "uq_users_email",
                        columnNames = {"email"}
                ),
                @UniqueConstraint(
                        name = "uq_users_provider_provider_id",
                        columnNames = {"provider", "provider_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /**
     * 탈퇴 계정의 username 익명화 접두사. 탈퇴 시 username은 {@code deleted_<id>}가 된다({@link #withdraw}).
     *
     * <p>로컬 회원가입에서는 예약어로 차단된다
     * ({@link com.mesh.hello.domain.auth.application.AuthService#signup}).
     * id가 유일하므로 이 접두사만 막으면 탈퇴 시 username 충돌이 구조적으로 불가능하다
     * (누군가 {@code deleted_<id>}를 선점하면 해당 id 사용자의 탈퇴가 유니크 제약 위반으로 롤백된다).</p>
     */
    public static final String WITHDRAWN_USERNAME_PREFIX = "deleted_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // unique 제약은 @Table uniqueConstraints(uq_users_username)로 관리한다.
    @Column(nullable = false)
    private String username;

    /** BCrypt 해시 문자열. 카카오 유저는 랜덤 BCrypt 해시로 채운다(평문 금지). */
    @Column(nullable = false)
    private String password;

    @Column
    private String nickname;

    /** 보유 포인트 총합. 실제 적립/차감 내역은 {@code PointHistory}(DB)로 관리한다. */
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

    // ── 탈퇴용 ───────────────────────────────────────────────────────────────

    /**
     * 탈퇴 여부. {@code true}면 탈퇴 처리된 계정.
     * 탈퇴 시 username·nickname·email·providerId를 익명화한다.
     */
    @Column(nullable = false)
    private boolean deleted = false;

    /** 탈퇴 처리 시각. 탈퇴 전에는 null. */
    @Column
    private LocalDateTime deletedAt;

    // ── 가입 보강용 ───────────────────────────────────────────────────────────

    /**
     * 이메일 주소. 길이 100, 유니크.
     *
     * <p>nullable = true인 이유: 카카오 가입자가 이메일 제공 동의를 하지 않으면 이메일이 없다.
     * NOT NULL로 하면 카카오 로그인이 깨진다.
     * MySQL은 NULL 값의 유니크 제약 중복을 허용하므로, unique + nullable을 함께 사용한다.</p>
     * unique 제약은 @Table uniqueConstraints(uq_users_email)로 관리한다.
     */
    @Column(length = 100)
    private String email;

    /** 개인정보 수집·이용 동의 여부. */
    @Column(nullable = false)
    private boolean privacyAgreed = false;

    /** 개인정보 수집·이용 동의 시각. 동의 전에는 null. */
    @Column
    private LocalDateTime privacyAgreedAt;

    // ── 생성자 / 팩토리 메서드 ────────────────────────────────────────────────

    /**
     * LOCAL 회원가입용 정적 팩토리 메서드.
     *
     * @param username        내부 로그인 식별자
     * @param encodedPassword BCrypt 해시 비밀번호(평문 금지)
     * @param nickname        닉네임. null 또는 공백이면 username을 닉네임으로 사용
     * @param email           이메일 주소. 없으면 null
     * @param privacyAgreed   개인정보 수집·이용 동의 여부
     */
    public static User createLocal(String username, String encodedPassword,
                                   String nickname, String email, boolean privacyAgreed) {
        User user = new User();
        user.username = username;
        user.password = encodedPassword;
        user.nickname = (nickname == null || nickname.isBlank()) ? username : nickname;
        user.email = email;
        user.points = 0L;
        user.provider = Provider.LOCAL;
        user.providerId = null;
        user.deleted = false;
        user.privacyAgreed = privacyAgreed;
        user.privacyAgreedAt = privacyAgreed ? LocalDateTime.now() : null;
        return user;
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
        user.deleted = false;
        user.privacyAgreed = false;
        return user;
    }

    // ── 도메인 메서드 ─────────────────────────────────────────────────────────

    /**
     * 회원 탈퇴 처리.
     *
     * <p>개인 식별 정보를 익명화하고 탈퇴 상태로 전환한다.
     * 비밀번호는 BCrypt 인코딩이 필요하므로 호출자(서비스 레이어)가 인코딩된 값을 전달한다.</p>
     *
     * @param anonymizedPassword BCrypt로 인코딩된 의미 없는 임의 문자열
     */
    public void withdraw(String anonymizedPassword) {
        this.username = WITHDRAWN_USERNAME_PREFIX + this.id;
        this.nickname = "탈퇴한 사용자";
        this.password = anonymizedPassword;
        this.email = null;
        this.providerId = null;
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 비밀번호 변경.
     *
     * <p>{@code encodedPassword}는 BCrypt로 인코딩된 값이어야 한다(평문 금지).
     * 인코딩 책임은 서비스 레이어에 있다({@link com.mesh.hello.domain.user.application.UserService}).
     * {@code updatedAt}은 {@link BaseEntity}가 자동 갱신한다.</p>
     *
     * @param encodedPassword BCrypt 해시 비밀번호
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
