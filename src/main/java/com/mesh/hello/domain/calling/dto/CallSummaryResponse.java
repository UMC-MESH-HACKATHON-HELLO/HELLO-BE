package com.mesh.hello.domain.calling.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CallSummaryResponse {

    private final String roomId;
    private final String summary;
    private final String summaryType;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private CallSummaryResponse(CallSummary s) {
        this.roomId = s.getRoomId();
        this.summary = s.getSummary();
        this.summaryType = s.getSummaryType().name();
        this.createdAt = s.getCreatedAt();
        this.updatedAt = s.getUpdatedAt();
    }

    public static CallSummaryResponse from(CallSummary s) {
        return new CallSummaryResponse(s);
    }
}