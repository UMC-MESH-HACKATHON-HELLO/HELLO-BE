package com.mesh.hello.domain.stt.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 통화 강제 종료를 유발한 금지어 감지 이력. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForbiddenWordDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String matchedWord;

    @Column(nullable = false, length = 1000)
    private String utterance;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    public ForbiddenWordDetection(String roomId, String sessionId, String role,
                                   String matchedWord, String utterance) {
        this.roomId = roomId;
        this.sessionId = sessionId;
        this.role = role;
        this.matchedWord = matchedWord;
        this.utterance = utterance;
        this.detectedAt = LocalDateTime.now();
    }
}
