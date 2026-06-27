package com.mesh.hello.domain.auth.application;

import com.mesh.hello.domain.auth.dto.LoginRequest;
import com.mesh.hello.domain.auth.dto.LoginResponse;
import com.mesh.hello.domain.auth.dto.SignupRequest;
import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * 도우미 세션 인증. 익명 세션 위에 로그인을 얹는다.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 익명 sessionId를 전달받는 키(요청 헤더 / HttpSession 속성 공통). */
    private static final String SESSION_ID_KEY = "sessionId";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final SessionAccountRepository sessionAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 로그인. 인증 성공 시 SecurityContext를 세션에 명시적으로 저장하고(이후 JSESSIONID로 인증 유지),
     * 현재 익명 sessionId를 로그인 userId에 바인딩한다.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException e) {
            // 아이디 없음/비번 불일치 모두 동일 응답(계정 열거 방지)
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 수동 인증 시 컨텍스트를 세션에 직접 저장하지 않으면 다음 요청에서 인증이 유지되지 않는다.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        // 익명 sessionId ↔ userId 바인딩 (매칭·포인트가 sessionId로 계정 resolve 가능하도록)
        resolveAnonymousSessionId(httpRequest)
                .ifPresent(sessionId -> sessionAccountRepository.bind(sessionId, user.getId()));

        return new LoginResponse(user.getId(), user.getNickname(), user.getPoints());
    }

    /** 로그아웃. 인증 해제 + 세션 무효화 + 바인딩 해제. */
    public void logout(HttpServletRequest httpRequest) {
        resolveAnonymousSessionId(httpRequest).ifPresent(sessionAccountRepository::unbind);
        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** 최소 회원가입(범위 밖이지만 시연용). username 중복 체크 + BCrypt 저장. */
    @Transactional
    public Long signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();
        return userRepository.save(user).getId();
    }

    /**
     * 현재 요청에서 익명 sessionId를 추출한다.
     *
     * <p>[가정] 기존 익명 세션(CM102)은 WebSocket 핸드셰이크에서 클라이언트가 {@code sessionId}를
     * 쿼리파라미터/헤더로 전달하는 방식이며, HTTP 레벨의 서버측 저장소는 확인되지 않았다.
     * 따라서 여기서는 <b>요청 헤더 {@code sessionId} → HttpSession 속성 {@code sessionId}</b> 순으로
     * 읽는다고 가정한다. 실제 익명 세션 저장 방식이 다르면 이 메서드만 교체하면 된다.</p>
     */
    private Optional<String> resolveAnonymousSessionId(HttpServletRequest request) {
        String fromHeader = request.getHeader(SESSION_ID_KEY);
        if (fromHeader != null && !fromHeader.isBlank()) {
            return Optional.of(fromHeader);
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object attr = session.getAttribute(SESSION_ID_KEY);
            if (attr instanceof String s && !s.isBlank()) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
