package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.dto.HelpHistoryResponse;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpHistoryServiceTest {

    @Mock
    private CallSummaryRepository callSummaryRepository;

    @Mock
    private UserRepository userRepository;

    private HelpHistoryService helpHistoryService;

    private User helperUser() {
        return User.builder().username("helper1").password("pw").nickname("도우미").build();
    }

    @Test
    @DisplayName("category 미지정 시 전체 완료 기록을 페이징 조회한다")
    void getHelpHistory_withoutCategory() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);
        User user = helperUser();
        when(userRepository.findByUsername("helper1")).thenReturn(java.util.Optional.of(user));

        CallSummary summary = new CallSummary("room-1", "helpee-1", "helper-1", null, 90);
        summary.complete("transcript", "요약", CallSummary.CallCategory.KIOSK);
        Page<CallSummary> page = new PageImpl<>(List.of(summary));
        when(callSummaryRepository.findAllByHelperIdAndStatusOrderByCreatedAtDesc(
                eq(user.getId()), eq(CallSummary.SummaryStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(page);

        HelpHistoryResponse response = helpHistoryService.getHelpHistory("helper1", 0, 20, null);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.histories()).hasSize(1);
        assertThat(response.histories().get(0).category()).isEqualTo(CallSummary.CallCategory.KIOSK);
        verifyNoMoreInteractions(callSummaryRepository);
    }

    @Test
    @DisplayName("category 지정 시 해당 카테고리로 필터링해 조회한다")
    void getHelpHistory_withCategory() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);
        User user = helperUser();
        when(userRepository.findByUsername("helper1")).thenReturn(java.util.Optional.of(user));
        when(callSummaryRepository.findAllByHelperIdAndStatusAndCategoryOrderByCreatedAtDesc(
                eq(user.getId()), eq(CallSummary.SummaryStatus.COMPLETED), eq(CallSummary.CallCategory.ROAD_GUIDE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        HelpHistoryResponse response = helpHistoryService.getHelpHistory("helper1", 0, 20, "길찾기");

        assertThat(response.totalCount()).isZero();
        verify(callSummaryRepository).findAllByHelperIdAndStatusAndCategoryOrderByCreatedAtDesc(
                eq(user.getId()), eq(CallSummary.SummaryStatus.COMPLETED), eq(CallSummary.CallCategory.ROAD_GUIDE), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"전체"})
    @DisplayName("category가 '전체'면 필터 없이 조회한다")
    void getHelpHistory_allCategoryMeansNoFilter(String category) {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);
        User user = helperUser();
        when(userRepository.findByUsername("helper1")).thenReturn(java.util.Optional.of(user));
        when(callSummaryRepository.findAllByHelperIdAndStatusOrderByCreatedAtDesc(
                eq(user.getId()), eq(CallSummary.SummaryStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        helpHistoryService.getHelpHistory("helper1", 0, 20, category);

        verify(callSummaryRepository).findAllByHelperIdAndStatusOrderByCreatedAtDesc(
                eq(user.getId()), eq(CallSummary.SummaryStatus.COMPLETED), any(Pageable.class));
    }

    @Test
    @DisplayName("알 수 없는 category 값이면 INVALID_CATEGORY 예외를 던진다")
    void getHelpHistory_unknownCategoryThrows() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);
        when(userRepository.findByUsername("helper1")).thenReturn(java.util.Optional.of(helperUser()));

        assertThatThrownBy(() -> helpHistoryService.getHelpHistory("helper1", 0, 20, "등산"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CATEGORY);
    }

    @Test
    @DisplayName("page가 음수면 INVALID_PAGING 예외를 던진다")
    void getHelpHistory_negativePageThrows() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);

        assertThatThrownBy(() -> helpHistoryService.getHelpHistory("helper1", -1, 20, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PAGING);
    }

    @Test
    @DisplayName("size가 0 이하면 INVALID_PAGING 예외를 던진다")
    void getHelpHistory_zeroSizeThrows() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);

        assertThatThrownBy(() -> helpHistoryService.getHelpHistory("helper1", 0, 0, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PAGING);
    }

    @Test
    @DisplayName("size가 상한(100)을 초과하면 INVALID_PAGING 예외를 던진다")
    void getHelpHistory_oversizeThrows() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);

        assertThatThrownBy(() -> helpHistoryService.getHelpHistory("helper1", 0, 101, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PAGING);
    }

    @Test
    @DisplayName("존재하지 않는 유저면 UNAUTHORIZED 예외를 던진다")
    void getHelpHistory_unknownUserThrows() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);
        when(userRepository.findByUsername("ghost")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> helpHistoryService.getHelpHistory("ghost", 0, 20, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("category가 null이면 전체 조회로 처리한다")
    void getHelpHistory_categoryNullMeansNoFilter() {
        helpHistoryService = new HelpHistoryService(callSummaryRepository, userRepository);
        User user = helperUser();
        when(userRepository.findByUsername("helper1")).thenReturn(java.util.Optional.of(user));
        when(callSummaryRepository.findAllByHelperIdAndStatusOrderByCreatedAtDesc(
                eq(user.getId()), eq(CallSummary.SummaryStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        HelpHistoryResponse response = helpHistoryService.getHelpHistory("helper1", 0, 20, null);

        assertThat(response.totalCount()).isZero();
    }
}
