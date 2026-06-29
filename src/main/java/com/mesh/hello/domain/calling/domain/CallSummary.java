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
    private LocalDateTime createdAt;

    public CallSummary(String roomId, String helpeeSessionId, String helperSessionId,
                       String transcript, String summary) {
        this.roomId = roomId;
        this.helpeeSessionId = helpeeSessionId;
        this.helperSessionId = helperSessionId;
        this.transcript = transcript;
        this.summary = summary;
        this.createdAt = LocalDateTime.now();
    }
}