package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.dto.CategoryCount;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
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
        String key = HELPER_CATEGORY_PREFIX + helperSessionId;

        redisTemplate.opsForZSet().remove(HELPER_QUEUE, helperSessionId);
        redisTemplate.delete(key);

        if (!categoryCounts.isEmpty()) {
            Map<String, String> categoryMap = categoryCounts.stream()
                    .collect(Collectors.toMap(
                            categoryCount -> categoryCount.category().getLabel(),
                            categoryCount -> categoryCount.count().toString()
                    ));

            redisTemplate.opsForHash().putAll(key, categoryMap);
        }

        long sequence = redisTemplate.opsForValue()
                .increment(HELPER_SEQUENCE);

        redisTemplate.opsForZSet()
                .add(
                        HELPER_QUEUE,
                        helperSessionId,
                        sequence
                );
    }

    @Override
    public Optional<String> findWaitingHelper(
            CallSummary.CallCategory category
    ) {
        Set<ZSetOperations.TypedTuple<String>> helpers = redisTemplate.opsForZSet()
                .rangeWithScores(HELPER_QUEUE, 0, -1);

        if (helpers == null || helpers.isEmpty()) {
            return Optional.empty();
        }

        String maxSequenceTmp = redisTemplate.opsForValue()
                .get(HELPER_SEQUENCE);

        if (maxSequenceTmp == null) {
            throw new BusinessException(ErrorCode.NO_HELPER_SEQUENCE);
        }

        double maxSequence = Double.parseDouble(maxSequenceTmp);
        String selectedHelper = null;
        double highestScore = Double.NEGATIVE_INFINITY;

        for (ZSetOperations.TypedTuple<String> helperEntry : helpers) {
            String helper = helperEntry.getValue();
            Double sequence = helperEntry.getScore();

            if (helper == null || sequence == null) {
                continue;
            }

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

            double score =
                    createScore(
                            categoryCounts,
                            category.getLabel(),
                            sequence,
                            maxSequence
                    );

            if (score > highestScore) {
                highestScore = score;
                selectedHelper = helper;
            }
        }

        return Optional.ofNullable(selectedHelper);
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
        double x = categoryCounts.getOrDefault(helpeeCategory, 0L);
        double total = categoryCounts.values().stream()
                .mapToDouble(Long::doubleValue)
                .sum();
        return (x * x + 1)
                / (total + CallSummary.CallCategory.values().length) * 1000
                + (maxSequence - sequence) / maxSequence;
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

        String key =
                HELPEE_CATEGORY_PREFIX + helpeeSessionId;

        redisTemplate.opsForZSet().remove(HELPEE_QUEUE, helpeeSessionId);
        redisTemplate.delete(key);

        redisTemplate.opsForValue()
                .set(
                        key,
                        category.name()
                );

        long sequence = redisTemplate.opsForValue()
                .increment(HELPEE_SEQUENCE);

        redisTemplate.opsForZSet()
                .add(
                        HELPEE_QUEUE,
                        helpeeSessionId,
                        sequence
                );
    }

    @Override
    public Optional<String> findWaitingHelpee(
            List<CategoryCount> categoryCounts
    ) {
        Set<ZSetOperations.TypedTuple<String>> helpees = redisTemplate.opsForZSet()
                .rangeWithScores(HELPEE_QUEUE, 0, -1);

        if (helpees == null || helpees.isEmpty()) {
            return Optional.empty();
        }

        String maxSequenceTmp = redisTemplate.opsForValue()
                .get(HELPEE_SEQUENCE);

        if (maxSequenceTmp == null) {
            throw new BusinessException(ErrorCode.NO_HELPEE_SEQUENCE);
        }

        Map<String, Long> helperCategoryCounts = categoryCounts.stream()
                .collect(Collectors.toMap(
                        CategoryCount::getLabel,
                        CategoryCount::count
                ));
        double maxSequence = Double.parseDouble(maxSequenceTmp);
        String selectedHelpee = null;
        double highestScore = Double.NEGATIVE_INFINITY;

        for (ZSetOperations.TypedTuple<String> helpeeEntry : helpees) {
            String helpee = helpeeEntry.getValue();
            Double sequence = helpeeEntry.getScore();

            if (helpee == null || sequence == null) {
                continue;
            }

            String categoryKey =
                    HELPEE_CATEGORY_PREFIX + helpee;

            String helpeeCategory =
                    redisTemplate.opsForValue()
                            .get(categoryKey);
            String helpeeCategoryLabel = helpeeCategory == null
                    ? null
                    : CallSummary.CallCategory.valueOf(helpeeCategory).getLabel();

            double score =
                    createScore(
                            helperCategoryCounts,
                            helpeeCategoryLabel,
                            sequence,
                            maxSequence
                    );

            if (score > highestScore) {
                highestScore = score;
                selectedHelpee = helpee;
            }
        }

        return Optional.ofNullable(selectedHelpee);
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
