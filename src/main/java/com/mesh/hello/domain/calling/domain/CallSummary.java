package com.mesh.hello.domain.calling.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String helpeeSessionId;

    @Column(nullable = false)
    private String helperSessionId;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private int durationSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SummaryStatus status;

    private LocalDateTime createdAt;

    /** 통화 종료 직후, AI 요약이 완성되기 전 PENDING 상태로 먼저 저장한다. */
    public CallSummary(String roomId, String helpeeSessionId, String helperSessionId, int durationSec) {
        this.roomId = roomId;
        this.helpeeSessionId = helpeeSessionId;
        this.helperSessionId = helperSessionId;
        this.durationSec = durationSec;
        this.status = SummaryStatus.PENDING;
    }

    /** AI 요약이 완성되면 원문·요약 텍스트를 채우고 COMPLETED로 전환한다. */
    public void complete(String transcript, String summary) {
        this.transcript = transcript;
        this.summary = summary;
        this.status = SummaryStatus.COMPLETED;
        this.createdAt = LocalDateTime.now();
    }

    /** AI 요약 생성에 실패하면 원문만 남기고 FAILED로 전환한다. 재시도/재조회가 가능하도록 성공과 구분한다. */
    public void fail(String transcript) {
        this.transcript = transcript;
        this.status = SummaryStatus.FAILED;
        this.createdAt = LocalDateTime.now();
    }

    public enum SummaryStatus {
        PENDING, COMPLETED, FAILED
    }
}