package com.mesh.hello.domain.matching.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;
import jakarta.validation.constraints.NotNull;

public record HelpRequest(
        @NotNull(message = "category는 필수 입력 값입니다.")
        CallSummary.CallCategory category
) {}
