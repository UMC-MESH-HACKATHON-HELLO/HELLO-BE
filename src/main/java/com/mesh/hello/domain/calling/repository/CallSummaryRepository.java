package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {
    Optional<CallSummary> findTopByRoomIdOrderByCreatedAtDesc(String roomId);
    List<CallSummary> findAllByOrderByCreatedAtDesc();
}