package com.mesh.hello.domain.user.api;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.user.application.UserService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
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
                "anonymousUser_" + role.toLowerCase(),
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

    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 계정을 탈퇴 처리합니다. 세션이 즉시 만료됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이미 탈퇴한 계정"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "진행 중인 통화 있음")
    })
    @DeleteMapping("/users/me")
    public ApiResponse<Void> withdraw(
            Principal principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 세션 invalidate 전에 sessionId 먼저 확보 (순서 중요)
        HttpSession session = request.getSession(false);
        String sessionId = (session != null) ? session.getId() : null;

        // 탈퇴 처리 (UserService - username 기반)
        userService.withdrawByUsername(principal.getName());

        // SessionAccountRepository 바인딩 제거
        if (sessionId != null) {
            sessionAccountRepository.unbind(sessionId);
        }

        // 세션 무효화
        if (session != null) {
            session.invalidate();
        }

        // SecurityContext 초기화
        SecurityContextHolder.clearContext();

        // JSESSIONID 쿠키 만료 (maxAge=0, path=/)
        Cookie expiredCookie = new Cookie("JSESSIONID", "");
        expiredCookie.setMaxAge(0);
        expiredCookie.setPath("/");
        response.addCookie(expiredCookie);

        return ApiResponse.ok("회원 탈퇴가 완료되었습니다.", null);
    }
}
