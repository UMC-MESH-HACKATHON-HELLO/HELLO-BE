package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.dto.CategoryCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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

    /**
     * 카테고리별로 분리된 helper/helpee 대기열. 각 helper는 K개(카테고리 종류 수)
     * 큐 전부에 들어가고, 큐마다 그 카테고리에 대한 점수가 다르게 저장된다.
     * helpee는 자기 카테고리에 해당하는 큐 하나에만 들어간다.
     */
    private static final String HELPER_CATEGORY_QUEUE_PREFIX =
            "matching:helpers:queue:";
    private static final String HELPEE_CATEGORY_QUEUE_PREFIX =
            "matching:helpees:queue:";

    /**
     * 대기시간 보너스 계수. score = categoryScore - sequence * SEQUENCE_EPSILON.
     * sequence가 작을수록(먼저 등록될수록) score가 커지도록(덜 깎이도록) 만드는
     * 아주 작은 값이다. categoryScore가 최대 수천 단위인 것을 감안해, sequence가
     * 상당히 커져도(수백만 단위) categoryScore 우선순위를 잘 뒤집지 않을 정도로
     * 작게 잡았다. 필요하면 @Value로 외부 설정화해도 된다.
     */
    private static final double SEQUENCE_EPSILON = 1e-7;

    private final StringRedisTemplate redisTemplate;

    /*
     * ------------------------------------------------------------------
     * [Helper claim]
     * helper는 push 시점에 카테고리별 점수가 이미 확정되어 각 카테고리 큐에
     * 저장되어 있으므로(더 이상 maxSequence 같은 "현재 시점" 값에 의존하지
     * 않음), claim은 요청된 카테고리 큐에서 최고 점수 1명을 뽑아오는 것만으로
     * 끝난다. 스캔이 필요 없다 (O(log N)).
     *
     * 뽑힌 helper는 다른 K-1개 카테고리 큐에도 남아있으므로 전부 제거해야
     * 한다. 이 모든 과정(선택 + 전체 큐에서 제거 + metadata 삭제)을 하나의
     * Lua 스크립트로 묶어 원자적으로 처리한다.
     *
     * 주의: Redis Cluster 환경에서는 스크립트가 접근하는 여러 key(카테고리별
     * 큐 K개 + 마스터 큐)가 서로 다른 해시 슬롯에 흩어질 수 있어 이 형태
     * 그대로는 못 쓴다(CROSSSLOT). 단일 인스턴스/센티널 구성 기준이다.
     * ------------------------------------------------------------------
     */
    private static final String CLAIM_HELPER_SCRIPT = """
            local categoryQueueKey = KEYS[1]
            local masterQueueKey = KEYS[2]
            local categoryHashPrefix = ARGV[1]
            local categoryQueuePrefix = ARGV[2]

            local top = redis.call('ZREVRANGE', categoryQueueKey, 0, 0)
            if #top == 0 then
                return nil
            end
            local selected = top[1]

            redis.call('ZREM', masterQueueKey, selected)
            redis.call('DEL', categoryHashPrefix .. selected)

            -- 선택된 helper를 다른 모든 카테고리 큐에서도 제거한다.
            for i = 3, #ARGV do
                redis.call('ZREM', categoryQueuePrefix .. ARGV[i], selected)
            end

            return selected
            """;

    /*
     * ------------------------------------------------------------------
     * [Helpee claim]
     * 같은 카테고리를 기다리는 helpee들은 이번 claim 요청(특정 helper 기준)
     * 에서 전문성 점수(x, total)가 모두 동일하다 - 그 값은 "요청한 helper가
     * 그 카테고리를 얼마나 다뤄봤는가"로만 결정되고 helpee가 누구인지와는
     * 무관하기 때문이다. 따라서 카테고리 큐 하나 안에서는 항상 가장 오래
     * 기다린 사람(=sequence가 가장 작은 사람)이 최선의 후보다.
     *
     * 그래서 K개 카테고리 큐 각각에서 '가장 오래 기다린 1명'만 조회(각각
     * O(log N))한 뒤, 그렇게 뽑힌 최대 K명끼리만 점수를 비교해 최종 승자를
     * 고른다. 전체 대기열 스캔(O(N)) 대신 O(K log N)으로 끝난다.
     * ------------------------------------------------------------------
     */
    private static final String CLAIM_HELPEE_SCRIPT = """
            local masterQueueKey = KEYS[1]
            local categoryQueuePrefix = ARGV[1]
            local categoryHashPrefix = ARGV[2]
            local categoryCount = tonumber(ARGV[3])
            local epsilon = tonumber(ARGV[4])

            local total = 0
            local counts = {}
            local categoryNames = {}
            for i = 5, #ARGV, 2 do
                local catName = ARGV[i]
                local cnt = tonumber(ARGV[i + 1])
                counts[catName] = cnt
                total = total + cnt
                categoryNames[#categoryNames + 1] = catName
            end

            local bestCategory = nil
            local bestMember = nil
            local highestScore = -1e308

            for _, catName in ipairs(categoryNames) do
                local queueKey = categoryQueuePrefix .. catName
                local oldest = redis.call('ZRANGE', queueKey, 0, 0, 'WITHSCORES')
                if #oldest > 0 then
                    local member = oldest[1]
                    local sequence = tonumber(oldest[2])
                    local x = counts[catName] or 0
                    local score = ((x * x + 1) / (total + categoryCount)) * 1000
                            - sequence * epsilon

                    if score > highestScore then
                        highestScore = score
                        bestCategory = catName
                        bestMember = member
                    end
                end
            end

            if bestMember then
                redis.call('ZREM', masterQueueKey, bestMember)
                redis.call('ZREM', categoryQueuePrefix .. bestCategory, bestMember)
                redis.call('DEL', categoryHashPrefix .. bestMember)
                return bestMember
            end

            return nil
            """;

    private static final RedisScript<String> CLAIM_HELPER_REDIS_SCRIPT =
            new DefaultRedisScript<>(CLAIM_HELPER_SCRIPT, String.class);

    private static final RedisScript<String> CLAIM_HELPEE_REDIS_SCRIPT =
            new DefaultRedisScript<>(CLAIM_HELPEE_SCRIPT, String.class);

    private double computeCategoryScore(long x, long total, int categoryTypeCount) {
        return ((double) (x * x + 1) / (total + categoryTypeCount)) * 1000.0;
    }

    @Override
    public void pushHelper(
            String helperSessionId,
            List<CategoryCount> categoryCounts
    ) {
        String hashKey = HELPER_CATEGORY_PREFIX + helperSessionId;
        CallSummary.CallCategory[] allCategories = CallSummary.CallCategory.values();

        // 기존 등록 정리: 마스터 큐, 모든 카테고리별 큐, metadata 해시.
        redisTemplate.opsForZSet().remove(HELPER_QUEUE, helperSessionId);
        for (CallSummary.CallCategory cat : allCategories) {
            redisTemplate.opsForZSet()
                    .remove(HELPER_CATEGORY_QUEUE_PREFIX + cat.name(), helperSessionId);
        }
        redisTemplate.delete(hashKey);

        Map<String, Long> countByCategory = categoryCounts.stream()
                .collect(Collectors.toMap(
                        cc -> cc.category().name(),
                        CategoryCount::count
                ));

        if (!countByCategory.isEmpty()) {
            Map<String, String> categoryMap = countByCategory.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue().toString()
                    ));
            redisTemplate.opsForHash().putAll(hashKey, categoryMap);
        }

        long sequence = redisTemplate.opsForValue()
                .increment(HELPER_SEQUENCE);

        long total = countByCategory.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        // 카테고리별 점수를 등록 시점에 전부 확정해서 각 카테고리 큐에 저장한다.
        // 더 이상 claim 시점에 maxSequence 같은 "현재 시점" 값을 조회할 필요가
        // 없다 - 이 값들은 이후 다른 helper가 등록되어도 변하지 않는다.
        for (CallSummary.CallCategory cat : allCategories) {
            long x = countByCategory.getOrDefault(cat.name(), 0L);
            double score = computeCategoryScore(x, total, allCategories.length)
                    - sequence * SEQUENCE_EPSILON;

            redisTemplate.opsForZSet()
                    .add(HELPER_CATEGORY_QUEUE_PREFIX + cat.name(), helperSessionId, score);
        }

        redisTemplate.opsForZSet()
                .add(HELPER_QUEUE, helperSessionId, sequence);
    }

    /**
     * 후보 helper를 점수 기준으로 선택함과 동시에 대기열/카테고리 metadata에서
     * 원자적으로 제거(claim)한다. 반환된 helperSessionId는 이미 대기열에서
     * 빠진 상태이므로, 호출부에서 다시 {@link #removeHelper(String)}를 호출할
     * 필요가 없다 (호출하면 안 됨 - 다른 요청이 새로 등록한 helper를 지울 수 있음).
     */
    @Override
    public Optional<String> claimWaitingHelper(
            CallSummary.CallCategory category
    ) {
        CallSummary.CallCategory[] allCategories = CallSummary.CallCategory.values();

        List<String> args = new ArrayList<>();
        args.add(HELPER_CATEGORY_PREFIX);
        args.add(HELPER_CATEGORY_QUEUE_PREFIX);
        for (CallSummary.CallCategory cat : allCategories) {
            args.add(cat.name());
        }

        Object[] scriptArgs = args.toArray();

        String selected = redisTemplate.execute(
                CLAIM_HELPER_REDIS_SCRIPT,
                List.of(HELPER_CATEGORY_QUEUE_PREFIX + category.name(), HELPER_QUEUE),
                scriptArgs
        );

        return Optional.ofNullable(selected);
    }

    @Override
    public boolean removeHelper(String helperSessionId) {

        Long removed =
                redisTemplate.opsForZSet()
                        .remove(
                                HELPER_QUEUE,
                                helperSessionId
                        );

        if (removed == null || removed == 0) {
            // 마스터 큐에 없었다면(이미 매칭/claim되어 빠진 상태일 수 있음)
            // 다른 key도 건드리지 않는다. 그래야 그 사이 재등록된 helper의
            // metadata를 실수로 지우지 않는다.
            return false;
        }

        for (CallSummary.CallCategory cat : CallSummary.CallCategory.values()) {
            redisTemplate.opsForZSet()
                    .remove(HELPER_CATEGORY_QUEUE_PREFIX + cat.name(), helperSessionId);
        }

        redisTemplate.delete(
                HELPER_CATEGORY_PREFIX + helperSessionId
        );

        return true;
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

        String metaKey =
                HELPEE_CATEGORY_PREFIX + helpeeSessionId;

        redisTemplate.opsForZSet().remove(HELPEE_QUEUE, helpeeSessionId);

        String previousCategory = redisTemplate.opsForValue().get(metaKey);
        if (previousCategory != null) {
            redisTemplate.opsForZSet()
                    .remove(HELPEE_CATEGORY_QUEUE_PREFIX + previousCategory, helpeeSessionId);
        }
        redisTemplate.delete(metaKey);

        redisTemplate.opsForValue()
                .set(
                        metaKey,
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

        redisTemplate.opsForZSet()
                .add(
                        HELPEE_CATEGORY_QUEUE_PREFIX + category.name(),
                        helpeeSessionId,
                        sequence
                );
    }

    /**
     * 후보 helpee를 점수 기준으로 선택함과 동시에 대기열/카테고리 metadata에서
     * 원자적으로 제거(claim)한다. 호출부에서 {@link #removeHelpee(String)}를
     * 다시 호출하지 않아야 한다.
     */
    @Override
    public Optional<String> claimWaitingHelpee(
            List<CategoryCount> categoryCounts
    ) {
        CallSummary.CallCategory[] allCategories = CallSummary.CallCategory.values();

        Map<String, Long> countByCategory = categoryCounts.stream()
                .collect(Collectors.toMap(
                        cc -> cc.category().name(),
                        CategoryCount::count
                ));

        List<String> args = new ArrayList<>();
        args.add(HELPEE_CATEGORY_QUEUE_PREFIX);
        args.add(HELPEE_CATEGORY_PREFIX);
        args.add(String.valueOf(allCategories.length));
        args.add(String.valueOf(SEQUENCE_EPSILON));

        for (CallSummary.CallCategory cat : allCategories) {
            args.add(cat.name());
            args.add(String.valueOf(countByCategory.getOrDefault(cat.name(), 0L)));
        }

        Object[] scriptArgs = args.toArray(new String[0]);

        String selected = redisTemplate.execute(
                CLAIM_HELPEE_REDIS_SCRIPT,
                List.of(HELPEE_QUEUE),
                scriptArgs
        );

        return Optional.ofNullable(selected);
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

        String category = redisTemplate.opsForValue()
                .get(HELPEE_CATEGORY_PREFIX + helpeeSessionId);
        if (category != null) {
            redisTemplate.opsForZSet()
                    .remove(HELPEE_CATEGORY_QUEUE_PREFIX + category, helpeeSessionId);
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