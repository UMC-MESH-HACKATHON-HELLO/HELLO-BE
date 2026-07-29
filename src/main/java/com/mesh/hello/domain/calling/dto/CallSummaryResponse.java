package com.mesh.hello.domain.calling.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;

import java.time.LocalDateTime;

public record CallSummaryResponse(
        String roomId,
        String summary,
        int durationSec,
        LocalDateTime completedAt
) {

    public static CallSummaryResponse from(CallSummary summary) {
        return new CallSummaryResponse(
                summary.getRoomId(),
                summary.getSummary(),
                summary.getDurationSec(),
                summary.getCompletedAt()
        );
    }
}