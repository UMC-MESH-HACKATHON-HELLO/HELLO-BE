package com.mesh.hello.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 공통 감사(Auditing) 필드를 제공하는 베이스 클래스.
 *
 * <p>{@code @EnableJpaAuditing}은 {@link com.mesh.hello.HelloApplication}에 선언되어 있다.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** 레코드 최초 생성 시각. 생성 후 변경 불가. */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 레코드 마지막 수정 시각. */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
