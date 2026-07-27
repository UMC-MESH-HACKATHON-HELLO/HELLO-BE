package com.mesh.hello.domain.user.controller;

import com.mesh.hello.domain.user.application.UserService;
import com.mesh.hello.domain.user.dto.HelperInfoResponse;
import com.mesh.hello.global.common.response.ApiResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 도우미 전용 엔드포인트(인증 필요). SecurityConfig에서 {@code /api/v1/helper/**} authenticated.
 */
@RestController
@RequestMapping("/helper")
@RequiredArgsConstructor
public class HelperController {

    private final UserService userService;

    /** 로그인된 도우미 본인 정보(포인트 포함) 조회. */
    @GetMapping("/me")
    public ApiResponse<HelperInfoResponse> me(Principal principal) {
        return ApiResponse.ok("도우미 정보 조회 성공", userService.getMyInfo(principal.getName()));
    }
}
