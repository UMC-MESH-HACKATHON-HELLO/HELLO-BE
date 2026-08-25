package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {

    Optional<CallSummary> findTopByRoomIdOrderByIdDesc(String roomId);

    Page<CallSummary> findAllByHelperIdAndStatusOrderByCreatedAtDesc(
            Long helperId, CallSummary.SummaryStatus status, Pageable pageable);

    Page<CallSummary> findAllByHelperIdAndStatusAndCategoryOrderByCreatedAtDesc(
            Long helperId, CallSummary.SummaryStatus status, CallSummary.CallCategory category, Pageable pageable);
}