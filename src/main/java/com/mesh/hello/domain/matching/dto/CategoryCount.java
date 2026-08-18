package com.mesh.hello.domain.matching.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;

public record CategoryCount(
        CallSummary.CallCategory category,
        Long count
) {

}
