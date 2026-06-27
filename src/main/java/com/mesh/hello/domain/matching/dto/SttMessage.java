package com.mesh.hello.domain.matching.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SttMessage {
    private String roomId;
    private String text;
}