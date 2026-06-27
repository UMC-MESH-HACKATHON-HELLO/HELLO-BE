package com.mesh.hello.domain.auth.controller;

import com.mesh.hello.domain.auth.application.AuthService;
import com.mesh.hello.domain.auth.dto.LoginRequest;
import com.mesh.hello.domain.auth.dto.LoginResponse;
import com.mesh.hello.domain.auth.dto.SignupRequest;
import com.mesh.hello.domain.auth.dto.SignupResponse;
import com.mesh.hello.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @Operation(summary = "회원가입", description = "username/password/nickname으로 도우미 계정을 생성합니다. 가입 직후 자동 로그인은 하지 않습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 사용 중인 username")
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.of(201, "회원가입이 완료되었습니다.", authService.signup(request));
    }
}
