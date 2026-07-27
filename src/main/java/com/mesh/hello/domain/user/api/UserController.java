package com.mesh.hello.domain.user.api;

import com.mesh.hello.domain.user.dto.SessionReqDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    @PostMapping("/session")
    public Map<String, String> getSession(
            @RequestBody SessionReqDTO.GetSession roleRequest,
            HttpServletRequest request
    ) {
        // DTO에서 role 값을 꺼냅니다.
        String role = roleRequest.role().toString();

        // 1. 권한(Role) 목록 생성
        List<GrantedAuthority> authorities = AuthorityUtils.createAuthorityList(role);

        // 2. 커스텀 익명 인증 토큰 생성
        AnonymousAuthenticationToken customAnonymousToken = new AnonymousAuthenticationToken(
                "key_for_anonymous",
                "anonymousUser_" + role.toLowerCase(),
                authorities
        );

        // 3. SecurityContext에 저장
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(customAnonymousToken);
        SecurityContextHolder.setContext(context);

        // 4. 톰캣 HTTP 세션에 저장
        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        return Map.of("sessionId", session.getId());
    }

    @PostMapping("/session/end")
    public Map<String, String> endSession(
            HttpServletRequest request
    ) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();

        return Map.of("status", "success");
    }
}
