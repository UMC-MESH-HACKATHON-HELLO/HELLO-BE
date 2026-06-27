package com.mesh.hello.domain.calling.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String reporterSessionId;

    @Column(nullable = false)
    private String s3Key;

    public CallRecord(String roomId, String reporterSessionId, String s3Key) {
        this.roomId = roomId;
        this.reporterSessionId = reporterSessionId;
        this.s3Key = s3Key;
    }
}
