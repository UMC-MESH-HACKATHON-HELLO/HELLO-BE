package com.mesh.hello.domain.matching.application;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import com.mesh.hello.domain.matching.dto.CategoryCount;
import com.mesh.hello.domain.matching.repository.PriorityQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PriorityMatchingServiceTest {

    @Mock private PriorityQueueRepository priorityQueueRepository;
    @Mock private CallSummaryRepository callSummaryRepository;
    @Mock private SessionAccountRepository sessionAccountRepository;

    private PriorityMatchingService service;

    @BeforeEach
    void setUp() {
        service = new PriorityMatchingService(
                priorityQueueRepository,
                callSummaryRepository,
                sessionAccountRepository
        );
    }

    @Test
    @DisplayName("매칭 가능한 helper를 선점하면 helper 세션을 반환한다")
    void matchHelper_returnsRemovedHelper() {
        given(priorityQueueRepository.findWaitingHelper(CallSummary.CallCategory.SMARTPHONE))
                .willReturn(Optional.of("helper-1"));
        given(priorityQueueRepository.removeHelper("helper-1")).willReturn(true);

        Optional<String> result = service.matchHelper("helpee-1", CallSummary.CallCategory.SMARTPHONE);

        assertThat(result).contains("helper-1");
        verify(priorityQueueRepository).removeHelper("helper-1");
    }

    @Test
    @DisplayName("선점 경쟁에 세 번 실패하면 helpee를 원래 카테고리로 대기시킨다")
    void matchHelper_queuesHelpeeAfterThreeFailedClaims() {
        given(priorityQueueRepository.findWaitingHelper(CallSummary.CallCategory.KIOSK))
                .willReturn(Optional.of("helper-1"));
        given(priorityQueueRepository.removeHelper("helper-1")).willReturn(false);

        Optional<String> result = service.matchHelper("helpee-1", CallSummary.CallCategory.KIOSK);

        assertThat(result).isEmpty();
        verify(priorityQueueRepository, times(3)).removeHelper("helper-1");
        verify(priorityQueueRepository).pushHelpee("helpee-1", CallSummary.CallCategory.KIOSK);
    }

    @Test
    @DisplayName("로그인 helper는 계정 단위의 완료 이력으로 대기 helpee를 선택한다")
    void registerHelper_usesAccountHistoryAndReturnsHelpeeWithCategory() {
        List<CategoryCount> counts = List.of(
                new CategoryCount(CallSummary.CallCategory.SMARTPHONE, 4L)
        );
        given(sessionAccountRepository.findUserId("helper-session")).willReturn(Optional.of(7L));
        given(callSummaryRepository.findCompletedCategoryCountByHelperId(7L)).willReturn(counts);
        given(priorityQueueRepository.findWaitingHelpee(counts)).willReturn(Optional.of("helpee-1"));
        given(priorityQueueRepository.getHelpeeCategory("helpee-1"))
                .willReturn(Optional.of(CallSummary.CallCategory.SMARTPHONE));
        given(priorityQueueRepository.removeHelpee("helpee-1")).willReturn(true);

        Optional<PriorityMatchingService.MatchedHelpee> result = service.registerHelper("helper-session");

        assertThat(result).contains(new PriorityMatchingService.MatchedHelpee(
                "helpee-1", CallSummary.CallCategory.SMARTPHONE
        ));
        verify(priorityQueueRepository).findWaitingHelpee(counts);
    }

    @Test
    @DisplayName("대기 helpee가 없으면 비로그인 helper의 세션 이력을 저장한다")
    void registerHelper_queuesGuestHelperWithSessionHistory() {
        List<CategoryCount> counts = List.of(
                new CategoryCount(CallSummary.CallCategory.ROAD_GUIDE, 2L)
        );
        given(sessionAccountRepository.findUserId("guest-helper")).willReturn(Optional.empty());
        given(callSummaryRepository.findCompletedCategoriesByHelperSessionId("guest-helper"))
                .willReturn(counts);
        given(priorityQueueRepository.findWaitingHelpee(counts)).willReturn(Optional.empty());

        Optional<PriorityMatchingService.MatchedHelpee> result = service.registerHelper("guest-helper");

        assertThat(result).isEmpty();
        verify(priorityQueueRepository).pushHelper("guest-helper", counts);
    }

    @Test
    @DisplayName("카테고리 없는 오래된 helpee는 제거하고 다음 후보를 찾는다")
    void registerHelper_removesStaleHelpeeBeforeQueueingHelper() {
        List<CategoryCount> counts = List.of();
        given(sessionAccountRepository.findUserId("helper-1")).willReturn(Optional.empty());
        given(callSummaryRepository.findCompletedCategoriesByHelperSessionId("helper-1"))
                .willReturn(counts);
        given(priorityQueueRepository.findWaitingHelpee(anyList()))
                .willReturn(Optional.of("stale-helpee"))
                .willReturn(Optional.empty());
        given(priorityQueueRepository.getHelpeeCategory("stale-helpee")).willReturn(Optional.empty());

        service.registerHelper("helper-1");

        verify(priorityQueueRepository).removeHelpee("stale-helpee");
        verify(priorityQueueRepository).pushHelper("helper-1", counts);
    }
}
