package com.mesh.hello.domain.fcm.controller;

import com.mesh.hello.domain.fcm.application.FcmService;
import com.mesh.hello.domain.fcm.dto.TokenDeleteRequest;
import com.mesh.hello.domain.fcm.dto.TokenRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FcmController {

    private final FcmService fcmService;

    @PostMapping("/helper/fcm-token")
    public void register(
            // @AuthenticationPrincipal AuthMember user,
            // 토큰 기반 인증 완성되면 주석 해제
            @RequestBody TokenRequest request
    ) {
        // fcmService.saveToken(user.getId(), request.token());
        fcmService.saveToken(request.userId(), request.token());
    }

    @DeleteMapping("/helper/fcm-token")
    public void delete(
            // @AuthenticationPrincipal AuthMember user,
            // 토큰 기반 인증 완성되면 주석 해제
            @RequestBody TokenDeleteRequest request
    ) {
        // fcmService.saveToken(user.getId(), request.token());
        fcmService.deleteToken(request.userId());
    }
}
