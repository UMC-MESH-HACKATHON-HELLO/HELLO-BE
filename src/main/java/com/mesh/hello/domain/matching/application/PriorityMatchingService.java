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
    public Optional<MatchedHelpee> registerHelper(
            String helperSessionId
    ) {

        List<CategoryCount> categoryCounts = findHelperCategoryCounts(helperSessionId);

        // 무한루프 방지를 위한 attempt
        for (int attempt = 0; attempt < 3; attempt++) {

            Optional<String> helpee =
                    priorityQueueRepository.findWaitingHelpee(categoryCounts);

            if (helpee.isEmpty()) {
                break;
            }

            Optional<CallSummary.CallCategory> helpeeCategory =
                    priorityQueueRepository
                            .getHelpeeCategory(helpee.get());

            if (helpeeCategory.isEmpty()) {

                priorityQueueRepository.removeHelpee(
                        helpee.get()
                );
                continue;
            }

            if (priorityQueueRepository.removeHelpee(
                    helpee.get()
            )) {
                return Optional.of(new MatchedHelpee(
                        helpee.get(),
                        helpeeCategory.get()
                ));
            }
        }

        priorityQueueRepository.pushHelper(
                helperSessionId,
                categoryCounts
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

    public void restoreHelper(String helperSessionId) {
        priorityQueueRepository.pushHelper(
                helperSessionId,
                findHelperCategoryCounts(helperSessionId)
        );
    }

    public void restoreHelpee(
            String helpeeSessionId,
            CallSummary.CallCategory category
    ) {
        priorityQueueRepository.pushHelpee(helpeeSessionId, category);
    }

    public boolean removeWaitingHelper(String helperSessionId) {
        return priorityQueueRepository.removeHelper(helperSessionId);
    }

    public void removeWaitingParticipant(String sessionId) {
        priorityQueueRepository.removeHelper(sessionId);
        priorityQueueRepository.removeHelpee(sessionId);
    }

    private List<CategoryCount> findHelperCategoryCounts(String helperSessionId) {
        return sessionAccountRepository.findUserId(helperSessionId)
                .map(callSummaryRepository::findCompletedCategoryCountByHelperId)
                .orElseGet(() -> callSummaryRepository
                        .findCompletedCategoriesByHelperSessionId(helperSessionId));
    }

    public record MatchedHelpee(
            String sessionId,
            CallSummary.CallCategory category
    ) {
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
