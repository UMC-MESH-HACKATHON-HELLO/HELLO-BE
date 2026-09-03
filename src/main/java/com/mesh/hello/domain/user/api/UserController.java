package com.mesh.hello.domain.user.api;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.user.application.UserService;
import com.mesh.hello.domain.user.dto.ChangePasswordRequest;
import com.mesh.hello.domain.user.dto.SessionReqDTO;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ApiResponse;
import com.mesh.hello.global.common.response.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(name = "User", description = "사용자 API")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SessionAccountRepository sessionAccountRepository;

    // ──────────────────────────────────────────────
    // 기존 익명 세션 엔드포인트 (CM102 플로우 유지)
    // ──────────────────────────────────────────────

    @PostMapping("/session")
    public Map<String, String> getSession(
            @RequestBody SessionReqDTO.GetSession roleRequest,
            HttpServletRequest request
    ) {
        String role = roleRequest.role().toString();
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(role);
        AnonymousAuthenticationToken customAnonymousToken = new AnonymousAuthenticationToken(
                "key_for_anonymous",
                "anonymousUser_" + role.toLowerCase(Locale.ROOT),
                authorities
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(customAnonymousToken);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        return Map.of("sessionId", session.getId());
    }

    @PostMapping("/session/end")
    public Map<String, String> endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("status", "success");
    }

    // ──────────────────────────────────────────────
    // 회원 탈퇴
    // ──────────────────────────────────────────────

    @Operation(
        summary = "회원 탈퇴",
        description = "현재 로그인된 계정을 탈퇴 처리합니다. 요청 본문은 없으며 세션 인증이 필요합니다. "
                + "탈퇴가 완료되면 세션이 즉시 만료되고 JSESSIONID 쿠키가 제거됩니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "인증되지 않았거나, 이미 탈퇴했거나, 세션이 만료된 경우")
    })
    @DeleteMapping("/users/me")
    public ApiResponse<Void> withdraw(
            Principal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 탈퇴 처리 (UserService - username 기반)
        userService.withdrawByUsername(principal.getName());

        // 세션 정리는 반드시 트랜잭션 커밋 이후에 한다 (invalidateSessionAndClearCookie 참고)
        invalidateSessionAndClearCookie(request, response);

        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null);
    }

    // ──────────────────────────────────────────────
    // 비밀번호 변경
    // ──────────────────────────────────────────────

    @Operation(
        summary = "비밀번호 변경",
        description = "현재 로그인된 LOCAL 계정의 비밀번호를 변경합니다. 요청 본문은 currentPassword, newPassword "
                + "두 필드이며 세션 인증이 필요합니다. 변경이 완료되면 보안을 위해 세션이 즉시 만료되고 "
                + "JSESSIONID 쿠키가 제거되므로, 새 비밀번호로 다시 로그인해야 합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "변경 성공 (세션이 무효화됨 — 재로그인 필요)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "현재 비밀번호 불일치(PASSWORD_MISMATCH), 새 비밀번호 형식 오류(INVALID_PASSWORD_FORMAT), "
                        + "새 비밀번호가 현재와 동일(SAME_AS_CURRENT_PASSWORD), "
                        + "소셜 로그인 계정(SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "미인증 또는 세션 만료")
    })
    @PatchMapping("/users/password")
    public ApiResponse<Void> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 비밀번호 변경 (UserService - username 기반). @Transactional이라 이 호출이 반환되면 커밋된 상태다.
        userService.changePasswordByUsername(principal.getName(), request);

        // 세션 정리는 반드시 트랜잭션 커밋 이후에 한다 (invalidateSessionAndClearCookie 참고)
        invalidateSessionAndClearCookie(httpRequest, httpResponse);

        return ApiResponse.ok("비밀번호를 변경했습니다.", null);
    }

    /**
     * 세션을 무효화하고 SecurityContext를 초기화한 뒤 JSESSIONID 쿠키를 만료시킨다.
     *
     * <p><b>반드시 트랜잭션 커밋 이후에 호출해야 한다.</b> 서비스(트랜잭션) 안에서, 혹은 커밋 전에
     * 먼저 호출하면 "세션은 끊겼는데 DB는 롤백된" 불일치 상태가 생길 수 있다.</p>
     *
     * <p>쿠키 속성을 바꿀 일이 생기면 이 메서드 한 곳만 고치면 된다.</p>
     *
     * @param request  현재 HTTP 요청 (세션 조회에 사용)
     * @param response 현재 HTTP 응답 (JSESSIONID 만료 쿠키 작성에 사용)
     */
    private void invalidateSessionAndClearCookie(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            sessionAccountRepository.unbind(session.getId());
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        // JSESSIONID 쿠키 만료 (maxAge=0, path=/)
        Cookie expiredCookie = new Cookie("JSESSIONID", "");
        expiredCookie.setMaxAge(0);
        expiredCookie.setPath("/");
        response.addCookie(expiredCookie);
    }
}
