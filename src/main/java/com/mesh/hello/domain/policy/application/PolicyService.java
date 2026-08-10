package com.mesh.hello.domain.policy.application;

import com.mesh.hello.domain.policy.domain.Policy;
import com.mesh.hello.domain.policy.domain.PolicyType;
import com.mesh.hello.domain.policy.dto.PolicyResponse;
import com.mesh.hello.domain.policy.repository.PolicyRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;

    @Transactional(readOnly = true)
    public PolicyResponse getPolicy(String rawType) {
        PolicyType type = PolicyType.from(rawType);
        Policy policy = policyRepository.findByType(type)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));
        return PolicyResponse.from(policy);
    }
}
