package com.mesh.hello.domain.policy.domain;

import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;

public enum PolicyType {
    TERMS,
    PRIVACY;

    public static PolicyType from(String value) {
        try {
            return PolicyType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_POLICY_TYPE);
        }
    }
}
