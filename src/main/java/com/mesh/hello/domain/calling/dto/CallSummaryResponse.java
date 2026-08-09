package com.mesh.hello.domain.calling.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;

import java.time.LocalDateTime;

public record CallSummaryResponse(
        String roomId,
        String summary,
        CallSummary.CallCategory category,
        int durationSec,
        LocalDateTime completedAt
) {

    public static CallSummaryResponse from(CallSummary summary) {
        return new CallSummaryResponse(
                summary.getRoomId(),
                summary.getSummary(),
                summary.getCategory(),
                summary.getDurationSec(),
                summary.getCompletedAt()
        );
    }
}