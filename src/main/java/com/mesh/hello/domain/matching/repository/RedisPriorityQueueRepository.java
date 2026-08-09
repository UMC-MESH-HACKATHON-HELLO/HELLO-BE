package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RedisPriorityQueueRepository
        implements PriorityQueueRepository {

    private static final String HELPER_QUEUE = "matching:helpers";
    private static final String HELPEE_QUEUE = "matching:helpees";

    private static final String HELPER_CATEGORY_PREFIX =
            "matching:helper:categories:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void pushHelper(
            String helperSessionId,
            Set<CallSummary.CallCategory> categories
    ) {
        long sequence = redisTemplate.opsForZSet()
                .zCard(HELPER_QUEUE) + 1;

        redisTemplate.opsForZSet()
                .add(
                        HELPER_QUEUE,
                        helperSessionId,
                        sequence
                );

        String key = HELPER_CATEGORY_PREFIX + helperSessionId;

        redisTemplate.delete(key);

        if (!categories.isEmpty()) {
            redisTemplate.opsForSet()
                    .add(
                            key,
                            categories.stream()
                                    .map(Enum::name)
                                    .toArray(String[]::new)
                    );
        }
    }

    @Override
    public Optional<String> findWaitingHelper(
            Set<CallSummary.CallCategory> categories
    ) {
        Set<String> helpers = redisTemplate.opsForZSet()
                .range(HELPER_QUEUE, 0, -1);

        if (helpers == null || helpers.isEmpty()) {
            return Optional.empty();
        }

        String tempKey = "matching:temp:" + UUID.randomUUID();

        try {
            for (String helper : helpers) {

                String categoryKey =
                        HELPER_CATEGORY_PREFIX + helper;

                Set<String> helperCategories =
                        redisTemplate.opsForSet()
                                .members(categoryKey);

                if (helperCategories == null) {
                    continue;
                }

                long matchCount = categories.stream()
                        .map(Enum::name)
                        .filter(helperCategories::contains)
                        .count();

                /*
                 * score:
                 *
                 * matchCount가 가장 중요
                 * sequence는 같은 matchCount에서 FIFO 보장
                 *
                 * 예:
                 *
                 * matchCount = 3
                 * sequence   = 10
                 *
                 * score = 3_000_000_000 + (MAX - 10)
                 */
                Double sequence =
                        redisTemplate.opsForZSet()
                                .score(
                                        HELPER_QUEUE,
                                        helper
                                );

                if (sequence == null) {
                    continue;
                }

                double score =
                        createScore(matchCount, sequence);

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

    private double createScore(
            long matchCount,
            double sequence
    ) {
        /*
         * 카테고리는 최대 4개.
         *
         * matchCount가 1 증가하는 것이
         * sequence 차이보다 항상 우선되도록
         * 큰 값을 사용한다.
         */
        long priority = 1_000_000_000L;

        return matchCount * priority
                + (priority - sequence);
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
    public void pushHelpee(String helpeeSessionId) {

        long sequence =
                redisTemplate.opsForZSet()
                        .zCard(HELPEE_QUEUE) + 1;

        redisTemplate.opsForZSet()
                .add(
                        HELPEE_QUEUE,
                        helpeeSessionId,
                        sequence
                );
    }

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

        return Optional.of(helpee);
    }

    @Override
    public void removeHelpee(String helpeeSessionId) {

        redisTemplate.opsForZSet()
                .remove(
                        HELPEE_QUEUE,
                        helpeeSessionId
                );
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
}

/*
-------------------------------- Redis 구조
waiting:helpers
    ZSET
    member = helperSessionId
    score  = 입장 순서

helper:categories:{sessionId}
    SET
    ROAD_GUIDE
    SMARTPHONE
    ...

waiting:helpees
    ZSET
    member = helpeeSessionId
    score  = 입장 순서

-------------------------------- Redis 임시 ZSET 구조
matching:{helpeeSessionId}
    ZSET

    helper1 → 3000000001
    helper2 → 2000000002
    helper3 → 3000000003

여기서 앞자리 3은 카테고리 3개 일치, 뒤쪽은 FIFO를 위한 순서값이다.

-------------------------------- Redis 임시 ZSET 구조
 */
