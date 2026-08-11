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

    public Optional<String> registerHelper(
            String helperSessionId,
            Set<CallSummary.CallCategory> categories
    ) {
        // 무한루프 방지를 위한 attempt
        for (int attempt = 0; attempt < 3; attempt++) {

            Optional<String> helpee =
                    priorityQueueRepository.peekWaitingHelpee();

            if (helpee.isEmpty()) {
                break;
            }

            Set<CallSummary.CallCategory> helpeeCategories =
                    priorityQueueRepository
                            .getHelpeeCategories(helpee.get());

            long matchCount = helpeeCategories.stream()
                    .filter(categories::contains)
                    .count();

            if (matchCount == 0) {
                /*
                 * 현재 가장 오래 기다린 helpee와
                 * 카테고리가 하나도 맞지 않음.
                 *
                 * Helpee FIFO 정책이라면
                 * 이 helper는 일단 대기열에 들어간다.
                 */
                break;
            }

            if (priorityQueueRepository.removeHelpee(
                    helpee.get()
            )) {
                return helpee;
            }
        }

        priorityQueueRepository.pushHelper(
                helperSessionId,
                categories
        );

        return Optional.empty();
    }

    public Optional<String> matchHelper(
            String helpeeSessionId,
            Set<CallSummary.CallCategory> categories
    ) {
        // 무한루프 방지를 위한 attempt
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<String> helper =
                    priorityQueueRepository
                            .findWaitingHelper(
                                    categories
                            );

            if (helper.isEmpty()) {

                priorityQueueRepository
                        .pushHelpee(
                                helpeeSessionId,
                                categories
                        );

                return Optional.empty();
            }

            if (priorityQueueRepository
                    .removeHelper(helper.get())) {

                return helper;
            }
        }

        priorityQueueRepository.pushHelpee(
                helpeeSessionId,
                categories
        );
        return Optional.empty();  // 대기 중인 helper가 없음
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