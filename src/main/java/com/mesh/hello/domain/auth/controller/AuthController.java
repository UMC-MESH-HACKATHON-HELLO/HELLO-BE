package com.mesh.hello.domain.auth.controller;

import com.mesh.hello.domain.auth.application.AuthService;
import com.mesh.hello.domain.auth.dto.LoginRequest;
import com.mesh.hello.domain.auth.dto.LoginResponse;
import com.mesh.hello.domain.auth.dto.SignupRequest;
import com.mesh.hello.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "username/password로 로그인합니다. 인증은 JSESSIONID 쿠키로 유지됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        return ApiResponse.ok("로그인에 성공했습니다.", authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 만료시킵니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ApiResponse.ok("로그아웃되었습니다.", null);
    }

    @Operation(summary = "회원가입", description = "username/password/nickname으로 계정을 생성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "중복 username")
    })
    @PostMapping("/signup")
    public ApiResponse<Long> signup(@RequestBody SignupRequest request) {
        return ApiResponse.ok("회원가입이 완료되었습니다.", authService.signup(request));
    }
}
