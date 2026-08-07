package com.mesh.hello.domain.stt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TranscriptMessage {
    private String speakerSessionId;
    private String transcript;
    private boolean isFinal;
}