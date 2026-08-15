package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import com.mesh.hello.domain.matching.repository.PriorityQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PriorityMatchingService {

    private final PriorityQueueRepository priorityQueueRepository;
    private final CallSummaryRepository callSummaryRepository;

    public Optional<String> registerHelper(
            String helperSessionId
    ) {
        Set<CallSummary.CallCategory> categories =
                callSummaryRepository
                        .findCompletedCategoriesByHelperSessionId(
                                helperSessionId
                        );


        // 무한루프 방지를 위한 attempt
        for (int attempt = 0; attempt < 3; attempt++) {

            Optional<String> helpee =
                    priorityQueueRepository.peekWaitingHelpee();

            if (helpee.isEmpty()) {
                break;
            }

            Optional<CallSummary.CallCategory> helpeeCategory =
                    priorityQueueRepository
                            .getHelpeeCategory(helpee.get());

            if (helpeeCategory.isEmpty()) {
                if (priorityQueueRepository.removeHelpee(
                        helpee.get()
                )) {
                    continue;
                }
                continue;
            }

            if (!categories.contains(helpeeCategory.get())) {
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
            CallSummary.CallCategory category
    ) {
        // 무한루프 방지를 위한 attempt
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<String> helper =
                    priorityQueueRepository
                            .findWaitingHelper(
                                    category
                            );

            if (helper.isEmpty()) {

                priorityQueueRepository
                        .pushHelpee(
                                helpeeSessionId,
                                category
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
                category
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
