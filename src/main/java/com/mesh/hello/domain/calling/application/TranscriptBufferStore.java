package com.mesh.hello.domain.calling.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TranscriptBufferStore {

    private final ConcurrentHashMap<String, List<String>> buffers = new ConcurrentHashMap<>();

    public void append(String roomId, String text) {
        buffers.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(text);
    }

    public String flushAndGet(String roomId) {
        List<String> lines = buffers.remove(roomId);
        if (lines == null || lines.isEmpty()) return "";
        return String.join("\n", lines);
    }
}