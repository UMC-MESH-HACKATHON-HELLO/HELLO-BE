package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {
    Optional<CallRecord> findByRoomId(String roomId);
}
