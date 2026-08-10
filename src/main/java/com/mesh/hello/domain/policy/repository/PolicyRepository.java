package com.mesh.hello.domain.policy.repository;

import com.mesh.hello.domain.policy.domain.Policy;
import com.mesh.hello.domain.policy.domain.PolicyType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Optional<Policy> findByType(PolicyType type);
}
