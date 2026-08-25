package com.mesh.hello.domain.policy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mesh.hello.domain.policy.domain.Policy;
import com.mesh.hello.domain.policy.domain.PolicyType;
import com.mesh.hello.domain.policy.dto.PolicyResponse;
import com.mesh.hello.domain.policy.repository.PolicyRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    private PolicyService policyService;

    @Test
    @DisplayName("등록된 정책이 있으면 조회에 성공한다")
    void getPolicySuccess() {
        policyService = new PolicyService(policyRepository);
        Policy policy = new Policy(PolicyType.TERMS, "이용약관", "내용", "1.0", LocalDate.of(2026, 8, 1));
        when(policyRepository.findByType(PolicyType.TERMS)).thenReturn(Optional.of(policy));

        PolicyResponse response = policyService.getPolicy("TERMS");

        assertThat(response.type()).isEqualTo(PolicyType.TERMS);
        assertThat(response.title()).isEqualTo("이용약관");
    }

    @Test
    @DisplayName("정의되지 않은 type 값이면 INVALID_POLICY_TYPE 예외를 던진다")
    void getPolicyInvalidType() {
        policyService = new PolicyService(policyRepository);

        assertThatThrownBy(() -> policyService.getPolicy("FOO"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_POLICY_TYPE);
    }

    @Test
    @DisplayName("등록된 정책이 없으면 POLICY_NOT_FOUND 예외를 던진다")
    void getPolicyNotFound() {
        policyService = new PolicyService(policyRepository);
        when(policyRepository.findByType(PolicyType.PRIVACY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> policyService.getPolicy("PRIVACY"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);
    }
}
