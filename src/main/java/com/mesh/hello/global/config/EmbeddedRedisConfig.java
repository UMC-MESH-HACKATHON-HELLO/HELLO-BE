package com.mesh.hello.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;

@Configuration
@Profile("local") // 배포 환경에서는 redis 서버 따로 띄움
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        redisServer = new RedisServer();  // 6379 포트
        redisServer.start();
    }

    @PreDestroy
    public void stopRedis() throws IOException{
        if (redisServer != null) {
            redisServer.stop();
        }
    }
}