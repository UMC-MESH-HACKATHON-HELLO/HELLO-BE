package com.mesh.hello.domain.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이용약관/개인정보처리방침 등 정책 문서 한 건.
 *
 * <p>{@code type}에 유니크 제약을 걸어 유형당 최신 1건만 보관한다.
 * 개정 이력을 남겨야 하면 {@code (type, version)} 복합 유니크로 전환한다.</p>
 */
@Entity
@Table(
        name = "policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_policies_type", columnNames = "type")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyType type;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Policy(PolicyType type, String title, String content, String version, LocalDate effectiveDate) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.version = version;
        this.effectiveDate = effectiveDate;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
