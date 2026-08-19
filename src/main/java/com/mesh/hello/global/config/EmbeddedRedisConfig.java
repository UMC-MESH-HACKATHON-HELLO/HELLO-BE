package com.mesh.hello.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

@Slf4j
@Configuration
@Profile("local") // 배포 환경에서는 redis 서버 따로 띄움
public class EmbeddedRedisConfig {

    private static final int REDIS_PORT = 6379;
    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        if (isPortInUse(REDIS_PORT)) {
            log.info("Redis already running on port {}. Skipping embedded Redis start.", REDIS_PORT);
            return;
        }
        redisServer = new RedisServer(REDIS_PORT);
        redisServer.start();
        log.info("Embedded Redis started on port {}.", REDIS_PORT);
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    private boolean isPortInUse(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }
}