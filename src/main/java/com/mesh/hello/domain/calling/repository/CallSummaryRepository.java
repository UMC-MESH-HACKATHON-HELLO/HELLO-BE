package com.mesh.hello.domain.calling.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;

import java.util.List;
import java.util.Optional;

import com.mesh.hello.domain.matching.dto.CategoryCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CallSummaryRepository extends JpaRepository<CallSummary, Long> {

    Optional<CallSummary> findTopByRoomIdOrderByIdDesc(String roomId);

    default List<CategoryCount> findCompletedCategoriesByHelperSessionId(
            String helperSessionId
    ) {
        return findCategoriesByHelperSessionIdAndStatus(
                helperSessionId,
                CallSummary.SummaryStatus.COMPLETED
        );
    }

    default List<CategoryCount> findCompletedCategoryCountByHelperId(
            Long helperId
    ) {
        return findCategoryCountByHelperIdAndStatus(
                helperId,
                CallSummary.SummaryStatus.COMPLETED
        );
    }

    /*
     * 근데 과거 기록에 모든 카테고리가 다 있으면
     * 카테고리 기준 우선순위가 무조건 1순위라
     * 기록이 가장 많은 카테고리만 반환하게 하는게 더 좋을지도
     */
    @Query("""
            SELECT new com.mesh.hello.domain.matching.dto.CategoryCount(
                c.category,
                COUNT(c)
            )
            FROM CallSummary c
            WHERE c.helperSessionId = :helperSessionId
              AND c.status = :status
              AND c.category IS NOT NULL
            GROUP BY c.category
        """)
    List<CategoryCount> findCategoriesByHelperSessionIdAndStatus(
            @Param("helperSessionId") String helperSessionId,
            @Param("status") CallSummary.SummaryStatus status
    );

    @Query("""
            SELECT new com.mesh.hello.domain.matching.dto.CategoryCount(
                c.category,
                COUNT(c)
            )
            FROM CallSummary c
            WHERE c.helperId = :helperId
              AND c.status = :status
              AND c.category IS NOT NULL
            GROUP BY c.category
        """)
    List<CategoryCount> findCategoryCountByHelperIdAndStatus(
            @Param("helperId") Long helperId,
            @Param("status") CallSummary.SummaryStatus status
    );

}
