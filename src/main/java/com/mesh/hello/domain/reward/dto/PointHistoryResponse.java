package com.mesh.hello.domain.reward.dto;

import java.util.List;

public record PointHistoryResponse(
        long totalPoints,
        List<PointHistoryItem> histories
) {
}
