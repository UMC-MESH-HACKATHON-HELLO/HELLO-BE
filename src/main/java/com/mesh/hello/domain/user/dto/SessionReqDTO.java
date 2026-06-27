package com.mesh.hello.domain.user.dto;

import com.mesh.hello.domain.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class SessionReqDTO {
    public record GetSession(
            UserRole role
    ) {}
}
