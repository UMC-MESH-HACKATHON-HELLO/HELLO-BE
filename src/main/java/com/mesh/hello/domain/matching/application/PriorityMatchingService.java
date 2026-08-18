package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import com.mesh.hello.domain.matching.dto.CategoryCount;
import com.mesh.hello.domain.matching.repository.PriorityQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriorityMatchingService {

    private final PriorityQueueRepository priorityQueueRepository;
    private final CallSummaryRepository callSummaryRepository;
    private final SessionAccountRepository sessionAccountRepository;

    @Transactional
    public Optional<String> registerHelper(
            String helperSessionId
    ) {

        List<CategoryCount> categoryCount;

        Optional<Long> opt = sessionAccountRepository.findUserId(helperSessionId);

        if (opt.isPresent()) categoryCount = callSummaryRepository
                .findCompletedCategoryCountByHelperId(opt.get());
        else categoryCount = callSummaryRepository
                .findCompletedCategoriesByHelperSessionId(helperSessionId);

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

            if (categoryCount.contains(new CategoryCount(helpeeCategory.get(), 0L))) {
                /*
                 * 현재 가장 오래 기다린 helpee의 카테고리 관련 도움 기록이 없음
                 * 일단 대기
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
                categoryCount
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
