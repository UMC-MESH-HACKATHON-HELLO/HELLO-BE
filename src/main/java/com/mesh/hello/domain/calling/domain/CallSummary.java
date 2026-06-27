package com.mesh.hello.domain.calling.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public CallSummary(String roomId, String transcript, String summary) {
        this.roomId = roomId;
        this.transcript = transcript;
        this.summary = summary;
        this.createdAt = LocalDateTime.now();
    }
}