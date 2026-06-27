package com.mesh.hello.domain.calling.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "report")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 500)
    private String s3Key;

    @Column(length = 100)
    private String reporterSessionId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}