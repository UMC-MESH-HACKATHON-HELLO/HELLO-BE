package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {

    Optional<CallSummary> findTopByRoomIdOrderByIdDesc(String roomId);
}