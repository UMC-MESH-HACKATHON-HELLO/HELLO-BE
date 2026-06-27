package com.mesh.hello.global.websocket.audio;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@RequiredArgsConstructor
public class AudioWebSocketConfig implements WebSocketConfigurer {

    private final AudioStreamWebSocketHandler audioStreamWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioStreamWebSocketHandler, "/ws/audio")
                .setAllowedOriginPatterns("*");
    }
}