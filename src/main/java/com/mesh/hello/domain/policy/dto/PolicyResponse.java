package com.mesh.hello.domain.policy.dto;

import com.mesh.hello.domain.policy.domain.Policy;
import com.mesh.hello.domain.policy.domain.PolicyType;
import java.time.LocalDate;

public record PolicyResponse(
        PolicyType type,
        String title,
        String content,
        String version,
        LocalDate effectiveDate
) {
    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
                policy.getType(),
                policy.getTitle(),
                policy.getContent(),
                policy.getVersion(),
                policy.getEffectiveDate()
        );
    }
}
