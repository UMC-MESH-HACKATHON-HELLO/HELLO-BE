package com.mesh.hello.domain.reward.repository;

import com.mesh.hello.domain.reward.domain.PointHistory;
import java.util.List;

public interface PointHistoryRepository {

    PointHistory save(Long userId, long amount, String reason, String roomId);

    List<PointHistory> findAllByUserId(Long userId);
}
