package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;

import java.util.Optional;
import java.util.Set;

public interface PriorityQueueRepository {

    void pushHelper(
            String helperSessionId,
            Set<CallSummary.CallCategory> categories
    );

    Optional<String> findWaitingHelper(
            Set<CallSummary.CallCategory> categories
    );

    boolean removeHelper(String helperSessionId);

    Integer getWaitingHelperCount();

    void pushHelpee(
            String helpeeSessionId,
            Set<CallSummary.CallCategory> categories
    );

    Optional<String> popWaitingHelpee();

    Optional<String> peekWaitingHelpee();

    boolean removeHelpee(String helpeeSessionId);

    Integer getWaitingHelpeeCount();

    Set<CallSummary.CallCategory> getHelpeeCategories(
            String helpeeSessionId
    );
}
