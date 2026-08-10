package com.mesh.hello.domain.reward.repository;

import com.mesh.hello.domain.reward.domain.PointHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    Page<PointHistory> findAllByUserId(Long userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PointHistory p WHERE p.userId = :userId")
    long sumAmountByUserId(@Param("userId") Long userId);
}
