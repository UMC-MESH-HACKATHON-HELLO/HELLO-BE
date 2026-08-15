package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {

    Optional<CallSummary> findTopByRoomIdOrderByIdDesc(String roomId);

    default Set<CallSummary.CallCategory> findCompletedCategoriesByHelperSessionId(
            String helperSessionId
    ) {
        return findCategoriesByHelperSessionIdAndStatus(
                helperSessionId,
                CallSummary.SummaryStatus.COMPLETED
        );
    }

    /*
     * 근데 과거 기록에 모든 카테고리가 다 있으면
     * 카테고리 기준 우선순위가 무조건 1순위라
     * 기록이 가장 많은 카테고리만 반환하게 하는게 더 좋을지도
     *
     * 그리고 지금 도우미 userId가 아니라 helperSessionid 기준으로 조회 중이라
     * 도우미 userId로 조회하려면 CallSummary에 userId 컬럼을 추가해야 할듯
     */
    @Query("""
            SELECT DISTINCT c.category
            FROM CallSummary c
            WHERE c.helperSessionId = :helperSessionId
              AND c.status = :status
              AND c.category IS NOT NULL
            """)
    Set<CallSummary.CallCategory> findCategoriesByHelperSessionIdAndStatus(
            @Param("helperSessionId") String helperSessionId,
            @Param("status") CallSummary.SummaryStatus status
    );

}
