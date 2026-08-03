package com.mesh.hello.domain.fcm.controller;

import com.mesh.hello.domain.fcm.application.FcmService;
import com.mesh.hello.domain.fcm.dto.TokenDeleteRequest;
import com.mesh.hello.domain.fcm.dto.TokenRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TokenRequest tokenRequest
    ) {
        String username = userDetails.getUsername();  // 인증 정보에 username이 들어있다고 함
        fcmService.saveToken(username, tokenRequest.token());
    }

    @DeleteMapping("/helper/fcm-token")
    public void delete(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String username = userDetails.getUsername();
        fcmService.deleteToken(username);
    }
}
