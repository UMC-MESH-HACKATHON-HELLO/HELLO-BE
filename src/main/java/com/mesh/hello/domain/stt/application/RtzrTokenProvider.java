package com.mesh.hello.domain.stt.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class RtzrTokenProvider {

    private static final String AUTH_URL = "https://openapi.vito.ai/v1/authenticate";
    private static final Duration REFRESH_BUFFER = Duration.ofMinutes(5);

    private final OkHttpClient rtzrAuthHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public RtzrTokenProvider(
            @Qualifier("rtzrAuthHttpClient") OkHttpClient rtzrAuthHttpClient,
            @Value("${rtzr.client-id}") String clientId,
            @Value("${rtzr.client-secret}") String clientSecret) {
        this.rtzrAuthHttpClient = rtzrAuthHttpClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public synchronized String getAccessToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt.minus(REFRESH_BUFFER))) {
            refresh();
        }
        return cachedToken;
    }

    private void refresh() {
        RequestBody body = new FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();
        Request request = new Request.Builder().url(AUTH_URL).post(body).build();

        try (Response response = rtzrAuthHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException(
                        "RTZR 인증 실패: status=%d, body=%s".formatted(response.code(), responseBody));
            }
            AuthResponse auth = objectMapper.readValue(responseBody, AuthResponse.class);
            cachedToken = auth.accessToken();
            expiresAt = Instant.ofEpochSecond(auth.expireAt());
            log.info("RTZR 인증 토큰 갱신 완료 (만료: {})", expiresAt);
        } catch (IOException e) {
            throw new UncheckedIOException("RTZR 인증 토큰 발급 실패", e);
        }
    }

    private record AuthResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expire_at") long expireAt) {
    }
}
