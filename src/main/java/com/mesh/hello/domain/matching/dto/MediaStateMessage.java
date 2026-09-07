package com.mesh.hello.domain.matching.dto;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import jakarta.validation.constraints.NotNull;

public record MediaStateMessage(
        @NotNull(message = "mediaType은 필수 입력 값입니다.")
        MatchingRoom.MediaType mediaType,

        @NotNull(message = "state는 필수 입력 값입니다.")
        MatchingRoom.MediaState state
) {}
