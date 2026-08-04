package com.mesh.hello.domain.matching.repository;

import java.util.Optional;

public interface MatchingQueueRepository {
    void pushHelper(String helperSessionId);
    Optional<String> popWaitingHelper();
    void removeHelper(String helperSessionId);
    boolean isHelperWaiting(String helperSessionId);
    Integer getWaitingHelperCount();

    void pushHelpee(String helpeeSessionId);
    Optional<String> popWaitingHelpee();
    void removeHelpee(String helpeeSessionId);
    Integer getWaitingHelpeeCount();
}
