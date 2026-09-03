package com.mesh.hello.domain.stt.repository;

import com.mesh.hello.domain.stt.domain.ForbiddenWordDetection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForbiddenWordDetectionRepository extends JpaRepository<ForbiddenWordDetection, Long> {
}
