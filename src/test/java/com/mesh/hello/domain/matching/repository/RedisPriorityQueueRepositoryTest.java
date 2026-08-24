package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.matching.dto.CategoryCount;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisPriorityQueueRepositoryTest {

    private static RedisServer redisServer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private RedisPriorityQueueRepository repository;

    @BeforeAll
    static void startRedis() throws IOException {
        int port = findAvailablePort();
        redisServer = new RedisServer(port);
        redisServer.start();

        connectionFactory = new LettuceConnectionFactory("127.0.0.1", port);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        repository = new RedisPriorityQueueRepository(redisTemplate);
    }

    @Test
    @DisplayName("helpee 요청 카테고리 이력이 많은 helper를 먼저 선택한다")
    void findWaitingHelper_prioritizesMatchingCategoryHistory() {
        repository.pushHelper("helper-with-history", List.of(
                new CategoryCount(CallSummary.CallCategory.SMARTPHONE, 1L)
        ));
        repository.pushHelper("helper-without-history", List.of());

        assertThat(repository.claimWaitingHelper(CallSummary.CallCategory.SMARTPHONE))
                .contains("helper-with-history");
    }

    @Test
    @DisplayName("이력이 동일하면 먼저 대기한 helper를 선택한다")
    void findWaitingHelper_prioritizesEarlierHelperWhenScoresTie() {
        repository.pushHelper("first-helper", List.of());
        repository.pushHelper("second-helper", List.of());

        assertThat(repository.claimWaitingHelper(CallSummary.CallCategory.KIOSK))
                .contains("first-helper");
    }

    @Test
    @DisplayName("helper 등록 시 이력이 가장 많은 카테고리의 helpee를 먼저 선택한다")
    void findWaitingHelpee_prioritizesMatchingCategoryHistory() {
        repository.pushHelpee("road-guide-helpee", CallSummary.CallCategory.ROAD_GUIDE);
        repository.pushHelpee("smartphone-helpee", CallSummary.CallCategory.SMARTPHONE);

        assertThat(repository.claimWaitingHelpee(List.of(
                new CategoryCount(CallSummary.CallCategory.SMARTPHONE, 2L)
        ))).contains("smartphone-helpee");
    }

    @Test
    @DisplayName("helpee는 단일 카테고리만 저장하고 제거 시 카테고리도 함께 삭제한다")
    void helpeeStoresSingleCategoryAndCleansItUpOnRemoval() {
        repository.pushHelpee("helpee-1", CallSummary.CallCategory.KIOSK);

        assertThat(repository.getHelpeeCategory("helpee-1"))
                .contains(CallSummary.CallCategory.KIOSK);
        assertThat(repository.removeHelpee("helpee-1")).isTrue();
        assertThat(repository.getHelpeeCategory("helpee-1")).isEmpty();
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
