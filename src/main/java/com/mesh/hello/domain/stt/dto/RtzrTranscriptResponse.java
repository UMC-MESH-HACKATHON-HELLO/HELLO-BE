package com.mesh.hello.domain.stt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RtzrTranscriptResponse(
        int seq,
        @JsonProperty("start_at") long startAt,
        long duration,
        @JsonProperty("final") boolean isFinal,
        List<Alternative> alternatives) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alternative(String text, Float confidence, List<Word> words) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Word(
            String text,
            @JsonProperty("start_at") long startAt,
            long duration,
            Float confidence) {
    }
}
