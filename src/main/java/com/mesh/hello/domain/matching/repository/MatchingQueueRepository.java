package com.mesh.hello.domain.matching.repository;

import java.util.Optional;

public interface MatchingQueueRepository {
    void pushHelper(String helperSessionId);
    Optional<String> popWaitingHelper();

    /** 대기열에서 제거를 시도한다. 실제로 대기 중이어서 제거됐다면 true. */
    boolean removeHelper(String helperSessionId);
    Integer getWaitingHelperCount();

    void pushHelpee(String helpeeSessionId);
    Optional<String> popWaitingHelpee();
    void removeHelpee(String helpeeSessionId);
    Integer getWaitingHelpeeCount();
}
