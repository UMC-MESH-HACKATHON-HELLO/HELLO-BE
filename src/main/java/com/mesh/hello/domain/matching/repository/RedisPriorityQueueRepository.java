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

    private final StringRedisTemplate redisTemplate;

    /*
     * ------------------------------------------------------------------
     * Lua 스크립트: "점수 계산 -> 최고 점수 후보 선택 -> ZREM -> 카테고리 삭제"를
     * 하나의 원자적 연산으로 묶는다. 이 스크립트가 실행되는 동안에는 Redis가
     * 다른 클라이언트의 명령을 끼워 넣지 않으므로, 후보를 읽은 뒤 서비스 계층에서
     * 별도로 제거하던 기존 방식에서 발생하던 "그 사이 재등록" 경쟁 조건이 사라진다.
     *
     * 주의: 아래 스크립트는 헬퍼/헬피별로 동적인 카테고리 key(prefix + member)에
     * 접근한다. Redis Cluster 환경에서는 스크립트 내에서 접근하는 모든 key가
     * 동일 해시 슬롯에 있어야 하므로 이 형태 그대로는 사용할 수 없다(CROSSSLOT
     * 오류 발생). 단일 인스턴스/센티널 구성에서는 문제 없다.
     * ------------------------------------------------------------------
     */
    private static final String CLAIM_HELPER_SCRIPT = """
            local queueKey = KEYS[1]
            local sequenceKey = KEYS[2]
            local categoryPrefix = ARGV[1]
            local categoryLabel = ARGV[2]
            local categoryCount = tonumber(ARGV[3])
 
            local members = redis.call('ZRANGE', queueKey, 0, -1, 'WITHSCORES')
            if #members == 0 then
                return nil
            end
 
            local maxSequenceStr = redis.call('GET', sequenceKey)
            if not maxSequenceStr then
                return redis.error_reply('NO_HELPER_SEQUENCE')
            end
            local maxSequence = tonumber(maxSequenceStr)
 
            local selected = nil
            local highestScore = -1e308
 
            for i = 1, #members, 2 do
                local member = members[i]
                local sequence = tonumber(members[i + 1])
 
                local categoryKey = categoryPrefix .. member
                local hash = redis.call('HGETALL', categoryKey)
 
                local x = 0
                local total = 0
                local j = 1
                while j <= #hash do
                    local field = hash[j]
                    local value = tonumber(hash[j + 1])
                    total = total + value
                    if field == categoryLabel then
                        x = value
                    end
                    j = j + 2
                end
 
                local score = ((x * x + 1) / (total + categoryCount)) * 1000
                        + (maxSequence - sequence) / maxSequence
 
                if score > highestScore then
                    highestScore = score
                    selected = member
                end
            end
 
            if selected then
                redis.call('ZREM', queueKey, selected)
                redis.call('DEL', categoryPrefix .. selected)
                return selected
            end
 
            return nil
            """;

    private static final String CLAIM_HELPEE_SCRIPT = """
            local queueKey = KEYS[1]
            local sequenceKey = KEYS[2]
            local categoryPrefix = ARGV[1]
            local categoryCount = tonumber(ARGV[2])
 
            local counts = {}
            for i = 3, #ARGV, 2 do
                counts[ARGV[i]] = tonumber(ARGV[i + 1])
            end
            local total = 0
            for _, v in pairs(counts) do
                total = total + v
            end
 
            local members = redis.call('ZRANGE', queueKey, 0, -1, 'WITHSCORES')
            if #members == 0 then
                return nil
            end
 
            local maxSequenceStr = redis.call('GET', sequenceKey)
            if not maxSequenceStr then
                return redis.error_reply('NO_HELPEE_SEQUENCE')
            end
            local maxSequence = tonumber(maxSequenceStr)
 
            local selected = nil
            local highestScore = -1e308
 
            for i = 1, #members, 2 do
                local member = members[i]
                local sequence = tonumber(members[i + 1])
 
                local categoryKey = categoryPrefix .. member
                local helpeeCategory = redis.call('GET', categoryKey)
 
                local x = 0
                if helpeeCategory and counts[helpeeCategory] then
                    x = counts[helpeeCategory]
                end
 
                local score = ((x * x + 1) / (total + categoryCount)) * 1000
                        + (maxSequence - sequence) / maxSequence
 
                if score > highestScore then
                    highestScore = score
                    selected = member
                end
            end
 
            if selected then
                redis.call('ZREM', queueKey, selected)
                redis.call('DEL', categoryPrefix .. selected)
                return selected
            end
 
            return nil
            """;

    private static final RedisScript<String> CLAIM_HELPER_REDIS_SCRIPT =
            new DefaultRedisScript<>(CLAIM_HELPER_SCRIPT, String.class);

    private static final RedisScript<String> CLAIM_HELPEE_REDIS_SCRIPT =
            new DefaultRedisScript<>(CLAIM_HELPEE_SCRIPT, String.class);

    @Override
    public void pushHelper(
            String helperSessionId,
            List<CategoryCount> categoryCounts
    ) {
        String key = HELPER_CATEGORY_PREFIX + helperSessionId;

        redisTemplate.opsForZSet().remove(HELPER_QUEUE, helperSessionId);
        redisTemplate.delete(key);

        if (!categoryCounts.isEmpty()) {
            // Lua 스크립트에서 label 매핑 없이 바로 비교할 수 있도록
            // enum name()을 내부 저장 키로 사용한다. (외부 노출 값 아님)
            Map<String, String> categoryMap = categoryCounts.stream()
                    .collect(Collectors.toMap(
                            categoryCount -> categoryCount.category().name(),
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
        String selected = redisTemplate.execute(
                CLAIM_HELPER_REDIS_SCRIPT,
                List.of(HELPER_QUEUE, HELPER_SEQUENCE),
                HELPER_CATEGORY_PREFIX,
                category.name(),
                String.valueOf(CallSummary.CallCategory.values().length)
        );

        return Optional.ofNullable(selected);
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

        if (removed == null || removed == 0) {
            // ZSET에 없었다면(이미 매칭/claim되어 빠진 상태일 수 있음) category
            // metadata도 건드리지 않는다. 그래야 그 사이 재등록된 helper의
            // metadata를 실수로 지우지 않는다.
            return false;
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

    /**
     * 후보 helpee를 점수 기준으로 선택함과 동시에 대기열/카테고리 metadata에서
     * 원자적으로 제거(claim)한다. 호출부에서 {@link #removeHelpee(String)}를
     * 다시 호출하지 않아야 한다.
     */
    @Override
    public Optional<String> claimWaitingHelpee(
            List<CategoryCount> categoryCounts
    ) {
        List<String> args = new ArrayList<>();
        args.add(HELPEE_CATEGORY_PREFIX);
        args.add(String.valueOf(CallSummary.CallCategory.values().length));

        for (CategoryCount categoryCount : categoryCounts) {
            args.add(categoryCount.category().name());
            args.add(categoryCount.count().toString());
        }

        Object[] scriptArgs = args.toArray(new String[0]);

        String selected = redisTemplate.execute(
                CLAIM_HELPEE_REDIS_SCRIPT,
                List.of(HELPEE_QUEUE, HELPEE_SEQUENCE),
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
