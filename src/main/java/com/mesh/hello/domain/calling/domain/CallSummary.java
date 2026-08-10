package com.mesh.hello.domain.calling.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Arrays;

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

    @Enumerated(EnumType.STRING)
    private CallCategory category;

    @Column(nullable = false)
    private int durationSec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SummaryStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    /** 통화 종료 직후, AI 요약이 완성되기 전 PENDING 상태로 먼저 저장한다. */
    public CallSummary(String roomId, String helpeeSessionId, String helperSessionId, int durationSec) {
        this.roomId = roomId;
        this.helpeeSessionId = helpeeSessionId;
        this.helperSessionId = helperSessionId;
        this.durationSec = durationSec;
        this.status = SummaryStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /** AI 요약이 완성되면 원문·요약 텍스트·도움 카테고리를 채우고 COMPLETED로 전환한다. */
    public void complete(String transcript, String summary, CallCategory category) {
        this.transcript = transcript;
        this.summary = summary;
        this.category = category;
        this.status = SummaryStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /** AI 요약 생성에 실패하면 원문만 남기고 FAILED로 전환한다. 재시도/재조회가 가능하도록 성공과 구분한다. */
    public void fail(String transcript) {
        this.transcript = transcript;
        this.status = SummaryStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public enum SummaryStatus {
        PENDING, COMPLETED, FAILED
    }

    @Slf4j
    public enum CallCategory {
        ROAD_GUIDE("길찾기"),
        SMARTPHONE("스마트폰"),
        KIOSK("키오스크"),
        ETC("기타");

        private final String label;

        CallCategory(String label) {
            this.label = label;
        }

        @JsonValue
        public String getLabel() {
            return label;
        }

        /** LLM이 반환한 한글 표시값을 enum으로 매핑한다. 매칭되지 않으면 ETC로 fallback한다. */
        public static CallCategory fromLabel(String label) {
            return Arrays.stream(values())
                    .filter(category -> category.label.equals(label))
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("알 수 없는 category 값: {}", label);
                        return ETC;
                    });
        }
    }
}