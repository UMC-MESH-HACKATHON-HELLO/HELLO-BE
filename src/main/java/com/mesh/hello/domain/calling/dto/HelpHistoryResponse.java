package com.mesh.hello.domain.calling.dto;

import java.util.List;

public record HelpHistoryResponse(
        long totalCount,
        List<HelpHistoryItem> histories
) {
}
