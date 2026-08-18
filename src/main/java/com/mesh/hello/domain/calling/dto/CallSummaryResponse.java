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
        // category 컬럼 추가 이전에 완료된 레코드는 category가 null로 남아있으므로 ETC로 방어적 처리한다.
        CallSummary.CallCategory category = summary.getCategory() != null
                ? summary.getCategory()
                : CallSummary.CallCategory.ETC;

        return new CallSummaryResponse(
                summary.getRoomId(),
                summary.getSummary(),
                category,
                summary.getDurationSec(),
                summary.getCompletedAt()
        );
    }
}