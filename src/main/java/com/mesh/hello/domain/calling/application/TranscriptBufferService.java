package com.mesh.hello.domain.calling.application;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class TranscriptBufferService {

    private final ConcurrentHashMap<String, StringBuilder> buffers = new ConcurrentHashMap<>();

    public void append(String roomId, String speaker, String text) {
        buffers.computeIfAbsent(roomId, k -> new StringBuilder())
                .append("[").append(speaker).append("] ").append(text).append("\n");
    }

    public String peek(String roomId) {
        StringBuilder sb = buffers.get(roomId);
        return sb != null ? sb.toString() : "";
    }

    public String flushAndGet(String roomId) {
        StringBuilder sb = buffers.remove(roomId);
        return sb != null ? sb.toString() : "";
    }
}