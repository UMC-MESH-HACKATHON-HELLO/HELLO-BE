package com.mesh.hello.domain.matching.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;

public record HelpRequest(
        CallSummary.CallCategory category
) {}
