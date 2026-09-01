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
import jakarta.validation.Valid;
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

    @Operation(
        summary = "회원가입",
        description = "로컬 계정을 생성합니다. 필수 필드: username, password, passwordConfirm, email, privacyAgreed. "
                + "선택 필드: nickname. 각 필드의 형식 규칙은 요청 본문 스키마 설명을 참고하세요. "
                + "성공 시 result에 생성된 사용자 ID(Long)를 반환합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "요청 형식 오류 또는 예약어 사용. 응답 message로 원인을 구분합니다: "
                        + "\"사용자명은 영문/숫자/언더스코어 3~20자여야 합니다.\"(INVALID_USERNAME_FORMAT), "
                        + "\"비밀번호는 영문·숫자를 포함한 8~20자여야 합니다.\"(INVALID_PASSWORD_FORMAT), "
                        + "\"이메일 형식이 올바르지 않습니다.\"(INVALID_EMAIL_FORMAT), "
                        + "\"닉네임은 2~20자여야 합니다.\"(INVALID_NICKNAME_FORMAT), "
                        + "\"비밀번호와 비밀번호 확인이 일치하지 않습니다.\"(PASSWORD_CONFIRM_MISMATCH), "
                        + "\"개인정보 수집 및 이용에 동의해야 합니다.\"(PRIVACY_AGREEMENT_REQUIRED), "
                        + "\"예약어로 사용할 수 없는 사용자명 형식입니다.\"(RESERVED_USERNAME_PREFIX)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                description = "중복. 응답 message로 구분: "
                        + "\"이미 사용 중인 사용자명입니다.\"(DUPLICATE_USERNAME), "
                        + "\"이미 사용 중인 이메일입니다.\"(DUPLICATE_EMAIL)")
    })
    @PostMapping("/signup")
    public ApiResponse<Long> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok("회원가입이 완료되었습니다.", authService.signup(request));
    }
}
