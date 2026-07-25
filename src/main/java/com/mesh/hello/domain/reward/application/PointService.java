package com.mesh.hello.domain.reward.application;

import com.mesh.hello.domain.reward.domain.PointHistory;
import com.mesh.hello.domain.reward.dto.PointHistoryItem;
import com.mesh.hello.domain.reward.dto.PointHistoryResponse;
import com.mesh.hello.domain.reward.repository.PointHistoryRepository;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도우미 포인트 적립/조회.
 *
 * <p>적립 내역({@code PointHistory})은 인메모리로 보관하고, {@code User.points}는
 * 로그인 응답 등에서 바로 참조할 수 있는 캐시 값으로 함께 갱신한다.</p>
 */
@Service
@RequiredArgsConstructor
public class PointService {

    /** 통화 1건 정상 종료 시 도우미에게 적립되는 포인트. */
    private static final long CALL_COMPLETE_POINTS = 10L;
    private static final String CALL_COMPLETE_REASON = "도움 통화 완료";

    private final PointHistoryRepository pointHistoryRepository;
    private final UserRepository userRepository;

    /** 통화 정상 종료 시 도우미에게 포인트를 적립한다. */
    @Transactional
    public void awardCallCompletePoints(Long helperId, String roomId) {
        pointHistoryRepository.save(helperId, CALL_COMPLETE_POINTS, CALL_COMPLETE_REASON, roomId);
        userRepository.findById(helperId).ifPresent(user -> user.addPoints(CALL_COMPLETE_POINTS));
    }

    @Transactional(readOnly = true)
    public PointHistoryResponse getPointHistory(String username, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGING);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        List<PointHistory> histories = pointHistoryRepository.findAllByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(PointHistory::getCreatedAt).reversed())
                .toList();

        long totalPoints = histories.stream().mapToLong(PointHistory::getAmount).sum();

        List<PointHistoryItem> pageItems = histories.stream()
                .skip((long) page * size)
                .limit(size)
                .map(PointHistoryItem::from)
                .toList();

        return new PointHistoryResponse(totalPoints, pageItems);
    }
}
