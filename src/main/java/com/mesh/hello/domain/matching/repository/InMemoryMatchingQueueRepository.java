package com.mesh.hello.domain.matching.repository;

import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Repository
public class InMemoryMatchingQueueRepository implements MatchingQueueRepository {
    private final Queue<String> helperQueue = new ConcurrentLinkedQueue<>();
    private final Queue<String> helpeeQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void pushHelper(String helperSessionId) {
        if (!helperQueue.contains(helperSessionId)) {
            helperQueue.add(helperSessionId);
        }
    }

    @Override
    public Optional<String> popWaitingHelper() {
        return Optional.ofNullable(helperQueue.poll());
    }

    @Override
    public boolean removeHelper(String helperSessionId) {
        return helperQueue.remove(helperSessionId);
    }

    @Override
    public Integer getWaitingHelperCount() {
        return helperQueue.size();
    }

    @Override
    public void pushHelpee(String helpeeSessionId) {
        if (!helpeeQueue.contains(helpeeSessionId)) {
            helpeeQueue.add(helpeeSessionId);
        }
    }

    @Override
    public Optional<String> popWaitingHelpee() {
        return Optional.ofNullable(helpeeQueue.poll());
    }

    @Override
    public void removeHelpee(String helpeeSessionId) {
        helpeeQueue.remove(helpeeSessionId);
    }

    @Override
    public Integer getWaitingHelpeeCount() {
        return helpeeQueue.size();
    }
}
