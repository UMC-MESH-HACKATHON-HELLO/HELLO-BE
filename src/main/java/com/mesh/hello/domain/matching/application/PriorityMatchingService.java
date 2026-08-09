package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.repository.PriorityQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PriorityMatchingService {

    private final PriorityQueueRepository priorityQueueRepository;

    public void registerHelper(
            String helperSessionId,
            Set<CallSummary.CallCategory> categories
    ) {
        priorityQueueRepository.pushHelper(
                helperSessionId,
                categories
        );
    }

    public Optional<String> matchHelper(
            Set<CallSummary.CallCategory> categories
    ) {
        Optional<String> helper =
                priorityQueueRepository.findWaitingHelper(
                        categories
                );

        if (helper.isEmpty()) {
            return Optional.empty();
        }

        String helperSessionId =
                helper.get();

        boolean removed =
                priorityQueueRepository.removeHelper(
                        helperSessionId
                );

        /*
         * 다른 매칭 요청이 먼저 가져간 경우
         */
        if (!removed) {
            return matchHelper(categories);
        }

        return Optional.of(helperSessionId);
    }

    public void registerHelpee(
            String helpeeSessionId
    ) {
        priorityQueueRepository.pushHelpee(
                helpeeSessionId
        );
    }

    public Integer getWaitingHelperCount() {
        return priorityQueueRepository
                .getWaitingHelperCount();
    }

    public Integer getWaitingHelpeeCount() {
        return priorityQueueRepository
                .getWaitingHelpeeCount();
    }
}