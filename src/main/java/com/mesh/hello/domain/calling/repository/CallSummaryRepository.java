package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {
}