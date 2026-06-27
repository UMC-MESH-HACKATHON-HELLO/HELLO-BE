package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
}