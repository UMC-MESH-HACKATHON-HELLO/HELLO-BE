package com.mesh.hello.domain.stt.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SttStartMessage {
    private String roomId;
    private String role;  // "helper" 또는 "helpee"
}