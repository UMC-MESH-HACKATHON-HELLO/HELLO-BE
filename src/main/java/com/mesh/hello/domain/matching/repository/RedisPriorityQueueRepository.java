package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.dto.CategoryCount;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RedisPriorityQueueRepository
        implements PriorityQueueRepository {

    private static final String HELPER_QUEUE = "matching:helpers";
    private static final String HELPEE_QUEUE = "matching:helpees";

    private static final String HELPER_SEQUENCE = "matching:helpers:sequence";
    private static final String HELPEE_SEQUENCE = "matching:helpees:sequence";

    private static final String HELPER_CATEGORY_PREFIX =
            "matching:helper:categories:";
    private static final String HELPEE_CATEGORY_PREFIX =
            "matching:helpee:category:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void pushHelper(
            String helperSessionId,
            List<CategoryCount> categoryCounts
    ) {
        long sequence = redisTemplate.opsForValue()
                        .increment(HELPER_SEQUENCE);

        redisTemplate.opsForZSet()
                .add(
                        HELPER_QUEUE,
                        helperSessionId,
                        sequence
                );

        String key = HELPER_CATEGORY_PREFIX + helperSessionId;

        redisTemplate.delete(key);

        if (!categoryCounts.isEmpty()) {
            categoryCounts.forEach(categoryCount ->
                    redisTemplate.opsForHash()
                            .put(
                                    key,
                                    categoryCount.category().getLabel(),
                                    categoryCount.count().toString()
                                    // String으로 저장 안하면 나중에 조회했을 때 값이 이상하게 나올 수도 있을듯
                            )
            );
        }
    }

    @Override
    public Optional<String> findWaitingHelper(
            CallSummary.CallCategory category
    ) {
        Set<String> helpers = redisTemplate.opsForZSet()
                .range(HELPER_QUEUE, 0, -1);  // ZSET에 있는 모든 데이터를 조회하므로 helper가 많아지면 성능 이슈 발생할 수 있음

        if (helpers == null || helpers.isEmpty()) {
            return Optional.empty();
        }

        String tempKey = "matching:temp:" + UUID.randomUUID();

        try {
            for (String helper : helpers) {

                String categoryKey =
                        HELPER_CATEGORY_PREFIX + helper;

                Map<String, Long> categoryCounts =
                        redisTemplate.opsForHash()
                                .entries(categoryKey)
                                .entrySet().stream()
                                .collect(Collectors.toMap(
                                        entry -> (String) entry.getKey(),
                                        entry -> Long.parseLong((String) entry.getValue())
                                ));

                Double sequence =
                        redisTemplate.opsForZSet()
                                .score(
                                        HELPER_QUEUE,
                                        helper
                                );

                String maxSequenceTmp = redisTemplate.opsForValue()
                        .get(HELPER_SEQUENCE);

                if (maxSequenceTmp == null) throw new BusinessException(ErrorCode.NO_HELPER_SEQUENCE);

                double maxSequence = Double.parseDouble(maxSequenceTmp);

                if (sequence == null) {
                    continue;
                }

                double score =
                        createScore(
                                categoryCounts,
                                category.getLabel(),
                                sequence,
                                maxSequence
                        );

                redisTemplate.opsForZSet()
                        .add(
                                tempKey,
                                helper,
                                score
                        );
            }

            Set<String> result =
                    redisTemplate.opsForZSet()
                            .reverseRange(
                                    tempKey,
                                    0,
                                    0
                            );

            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }

            return result.stream().findFirst();

        } finally {
            redisTemplate.delete(tempKey);
        }
    }

    /**
     * 점수 계산식 : 라플라스 스무딩 + 들어온 순서에 따른 추가 점수 <br>
     * ( (helpeeCategory 기록 수)<sup>2</sup> + 1) / (전체 기록 수 + 카테고리 수) * 1000 + (maxSequence - sequence) / maxSequence <br>
     * <ul>
     * <li>sequence : 들어온 순서 값 (id라고 생각해도 됨)</li>
     * <li>maxSequence : 맨 마지막 순서 값 (맨 마지막 id 값이라고 생각해도 됨)</li>
     * </ul>
     */
    private double createScore(
            Map<String, Long> categoryCounts,
            String helpeeCategory,
            double sequence,
            double maxSequence
    ) {
        double x = categoryCounts.get(helpeeCategory).doubleValue();
        double total = categoryCounts.values().stream()
                .mapToDouble(Long::doubleValue)
                .sum();
        return (x * x + 1) / (total + categoryCounts.size()) * 1000 + (maxSequence - sequence) / maxSequence;
    }

    @Override
    public boolean removeHelper(String helperSessionId) {

        Long removed =
                redisTemplate.opsForZSet()
                        .remove(
                                HELPER_QUEUE,
                                helperSessionId
                        );

        redisTemplate.delete(
                HELPER_CATEGORY_PREFIX + helperSessionId
        );

        return removed != null && removed > 0;
    }

    @Override
    public Integer getWaitingHelperCount() {
        Long size =
                redisTemplate.opsForZSet()
                        .zCard(HELPER_QUEUE);

        return size == null
                ? 0
                : size.intValue();
    }

    @Override
    public void pushHelpee(
            String helpeeSessionId,
            CallSummary.CallCategory category
    ) {

        Long sequence = redisTemplate.opsForValue()
                .increment(HELPEE_SEQUENCE);

        redisTemplate.opsForZSet()
                .add(
                        HELPEE_QUEUE,
                        helpeeSessionId,
                        sequence
                );

        String key =
                HELPEE_CATEGORY_PREFIX + helpeeSessionId;

        redisTemplate.delete(key);

        redisTemplate.opsForValue()
                .set(
                        key,
                        category.name()
                );
    }

    // 완전한 원자성을 보장하지 X, 추후 개선 필요 (ZRANGE + ZREM)
    @Deprecated(forRemoval = true, since =
            "popWaitingHelpee() 대신 peekWaitingHelpee() + removeWaitingHelpee() 사용해주세요."
    )
    @Override
    public Optional<String> popWaitingHelpee() {

        Set<String> result =
                redisTemplate.opsForZSet()
                        .range(
                                HELPEE_QUEUE,
                                0,
                                0
                        );

        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        String helpee =
                result.iterator().next();

        redisTemplate.opsForZSet()
                .remove(
                        HELPEE_QUEUE,
                        helpee
                );
        redisTemplate.delete(
                HELPEE_CATEGORY_PREFIX + helpee
        );

        return Optional.of(helpee);
    }

    @Override
    public Optional<String> peekWaitingHelpee() {

        Set<String> result =
                redisTemplate.opsForZSet()
                        .range(
                                HELPEE_QUEUE,
                                0,
                                0
                        );

        if (result == null || result.isEmpty()) {
            return Optional.empty();
        }

        return result.stream().findFirst();
    }

    @Override
    public boolean removeHelpee(String helpeeSessionId) {

        Long removed =
                redisTemplate.opsForZSet()
                        .remove(
                                HELPEE_QUEUE,
                                helpeeSessionId
                        );

        if (removed == null || removed == 0) {
            return false;
        }

        redisTemplate.delete(
                HELPEE_CATEGORY_PREFIX + helpeeSessionId
        );

        return true;
    }

    @Override
    public Integer getWaitingHelpeeCount() {

        Long size =
                redisTemplate.opsForZSet()
                        .zCard(HELPEE_QUEUE);

        return size == null
                ? 0
                : size.intValue();
    }

    @Override
    public Optional<CallSummary.CallCategory> getHelpeeCategory(
            String helpeeSessionId
    ) {

        String key =
                HELPEE_CATEGORY_PREFIX + helpeeSessionId;

        String value =
                redisTemplate.opsForValue()
                        .get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(
                CallSummary.CallCategory.valueOf(value)
        );
    }
}

/*
 * -------------------------------- Matching 흐름
 *
 * Helper 등록
 *   ↓
 * helperSessionId + helper의 과거 카테고리
 *   ↓
 * PriorityQueueRepository
 *   ↓
 * Redis에 대기 helper + 카테고리 저장
 *
 *
 * Helpee 매칭 요청
 *   ↓
 * helpee의 요청 카테고리
 *   ↓
 * PriorityQueueRepository.findWaitingHelper()
 *   ↓
 * 대기 중인 helper들의 과거 카테고리와 비교
 *   ↓
 * 카테고리 일치 개수 계산
 *   ↓
 * 일치 개수가 많을수록 높은 우선순위
 *   ↓
 * 동일한 일치 개수라면 먼저 들어온 helper 우선
 *   ↓
 * 가장 적합한 helper 선택
 *   ↓
 * PriorityQueueRepository.removeHelper()
 *   ↓
 * 제거에 성공하면 매칭 완료
 *
 *
 * -------------------------------- Redis 구조
 *
 * matching:helpers
 *   ZSET
 *   member = helperSessionId
 *   score  = helper 대기 순서
 *
 * matching:helper:categories:{helperSessionId}
 *   SET
 *   = helper가 과거에 처리한 카테고리
 *
 * matching:helpees
 *   ZSET
 *   member = helpeeSessionId
 *   score  = helpee 대기 순서
 *
 * matching:helpee:category:{helpeeSessionId}
 *   STRING
 *   = helpee가 요청한 단일 카테고리
 *
 *
 * -------------------------------- Helper 우선순위
 *
 * 현재 helpee의 요청 카테고리와
 * helper의 과거 카테고리를 비교하여
 * 일치 여부를 계산한다.
 *
 * 예:
 *
 * Helpee
 *   SMARTPHONE
 *
 * Helper A
 *   ROAD_GUIDE
 *   SMARTPHONE
 *   KIOSK
 *   ETC
 *   → 일치
 *
 * Helper B
 *   ROAD_GUIDE
 *   ETC
 *   → 불일치
 *
 * Helper C
 *   SMARTPHONE
 *   KIOSK
 *   → 일치
 *
 * 최종 우선순위:
 *
 *   Helper A → Helper C → Helper B
 *
 *
 * -------------------------------- 동시성
 *
 * findWaitingHelper()와 removeHelper() 사이에
 * 다른 매칭 요청이 먼저 helper를 가져갈 수 있다.
 *
 * 따라서 removeHelper()의 반환값으로
 * 실제 선점 성공 여부를 확인한다.
 *
 * 제거에 실패하면 다른 helper를 다시 조회한다.
 */
