package com.mesh.hello.domain.calling.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_summary")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CallSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String roomId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SummaryType summaryType = SummaryType.REALTIME;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public void updateWithReportSummary(String newSummary) {
        this.summary = newSummary;
        this.summaryType = SummaryType.REPORT;
        this.updatedAt = LocalDateTime.now();
    }

    public enum SummaryType {
        REALTIME, REPORT
    }
}