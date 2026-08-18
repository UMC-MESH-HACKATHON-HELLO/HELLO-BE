package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.dto.CategoryCount;

import java.util.List;
import java.util.Optional;

public interface PriorityQueueRepository {

    void pushHelper(
            String helperSessionId,
            List<CategoryCount> categoryCounts
    );

    Optional<String> findWaitingHelper(
            CallSummary.CallCategory category
    );

    boolean removeHelper(String helperSessionId);

    Integer getWaitingHelperCount();

    void pushHelpee(
            String helpeeSessionId,
            CallSummary.CallCategory category
    );

    Optional<String> findWaitingHelpee(List<CategoryCount> categoryCounts);

    boolean removeHelpee(String helpeeSessionId);

    Integer getWaitingHelpeeCount();

    Optional<CallSummary.CallCategory> getHelpeeCategory(
            String helpeeSessionId
    );
}
