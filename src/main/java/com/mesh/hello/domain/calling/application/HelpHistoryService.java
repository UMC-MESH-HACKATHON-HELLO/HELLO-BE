package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.domain.CallSummary.CallCategory;
import com.mesh.hello.domain.calling.dto.HelpHistoryItem;
import com.mesh.hello.domain.calling.dto.HelpHistoryResponse;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 도우미 본인이 수행한 도움 통화 기록 조회. */
@Service
@RequiredArgsConstructor
public class HelpHistoryService {

    private static final String ALL_CATEGORY_LABEL = "전체";
    private static final int MAX_PAGE_SIZE = 100;

    private final CallSummaryRepository callSummaryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public HelpHistoryResponse getHelpHistory(String username, int page, int size, String category) {
        if (page < 0 || size <= 0 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PAGING);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        CallCategory resolvedCategory = resolveCategory(category);

        Page<CallSummary> summaries = resolvedCategory == null
                ? callSummaryRepository.findAllByHelperIdAndStatusOrderByCreatedAtDesc(
                        user.getId(), CallSummary.SummaryStatus.COMPLETED, pageable)
                : callSummaryRepository.findAllByHelperIdAndStatusAndCategoryOrderByCreatedAtDesc(
                        user.getId(), CallSummary.SummaryStatus.COMPLETED, resolvedCategory, pageable);

        List<HelpHistoryItem> items = summaries.stream()
                .map(HelpHistoryItem::from)
                .toList();

        return new HelpHistoryResponse(summaries.getTotalElements(), items);
    }

    /** category 파라미터가 없거나 "전체"면 필터 없음(null), 알 수 없는 값이면 예외를 던진다. */
    private CallCategory resolveCategory(String category) {
        if (category == null || category.isBlank() || category.equals(ALL_CATEGORY_LABEL)) {
            return null;
        }
        return Arrays.stream(CallCategory.values())
                .filter(c -> c.getLabel().equals(category))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CATEGORY));
    }
}
