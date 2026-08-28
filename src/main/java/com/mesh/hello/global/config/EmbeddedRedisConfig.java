package com.mesh.hello.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

@Slf4j
@Configuration
@Profile("local") // 배포 환경에서는 redis 서버 따로 띄움
public class EmbeddedRedisConfig {

    private static final int REDIS_PORT = 6379;
    private RedisServer redisServer;

    /**
     * 내장 Redis를 시작한다.
     *
     * <p>사전 포트 검사(TOCTOU 위험 + 6379를 점유한 게 Redis가 아닐 수 있음)는 하지 않는다.
     * 그냥 시작을 시도하고, 실패하면(대부분 포트 충돌) 경고만 남기고 기동을 계속한다.
     * 이미 외부/로컬 Redis가 6379에 떠 있다면 그것을 그대로 쓰면 된다.</p>
     */
    @PostConstruct
    public void startRedis() {
        try {
            redisServer = new RedisServer(REDIS_PORT);
            redisServer.start();
            log.info("내장 Redis 를 포트 {} 에서 시작했습니다.", REDIS_PORT);
        } catch (Exception e) {
            // 시작 실패는 기동을 막지 않는다. 원인 대부분은 포트 6379 점유.
            redisServer = null;
            log.warn("내장 Redis 시작에 실패했습니다 — 포트 {} 가 이미 사용 중일 수 있습니다. "
                    + "외부(또는 이미 실행 중인) Redis 사용 여부를 확인하세요. (원인: {})",
                    REDIS_PORT, e.toString());
        }
    }

    @PreDestroy
    public void stopRedis() {
        if (redisServer == null) {
            return;
        }
        try {
            redisServer.stop();
        } catch (Exception e) {
            log.warn("내장 Redis 종료 중 오류가 발생했습니다. (원인: {})", e.toString());
        }
    }
}
