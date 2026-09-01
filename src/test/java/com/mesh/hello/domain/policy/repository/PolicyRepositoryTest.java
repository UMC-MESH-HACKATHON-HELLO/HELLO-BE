package com.mesh.hello.domain.policy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mesh.hello.domain.policy.domain.Policy;
import com.mesh.hello.domain.policy.domain.PolicyType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * data.sql로 시드된 TERMS/PRIVACY 레코드가 실제로 조회되는지 검증한다.
 * application.yaml에 이미 설정된 H2 DataSource를 그대로 사용한다(자동 교체 비활성화).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PolicyRepositoryTest {

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    @DisplayName("data.sql로 등록된 TERMS 정책이 조회된다")
    void findsSeededTerms() {
        Optional<Policy> policy = policyRepository.findByType(PolicyType.TERMS);

        assertThat(policy).isPresent();
        assertThat(policy.get().getTitle()).isEqualTo("이용약관");
    }

    @Test
    @DisplayName("data.sql로 등록된 PRIVACY 정책이 조회된다")
    void findsSeededPrivacy() {
        Optional<Policy> policy = policyRepository.findByType(PolicyType.PRIVACY);

        assertThat(policy).isPresent();
        assertThat(policy.get().getTitle()).isEqualTo("개인정보처리방침");
    }
}
