package com.mesh.hello.domain.stt.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AudioChunkMessage {
    private String audio;  // Base64 인코딩된 PCM 데이터
    private String roomId;
}