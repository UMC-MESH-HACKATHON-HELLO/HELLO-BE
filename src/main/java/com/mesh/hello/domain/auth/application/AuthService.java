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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * 도우미 세션 인증. 익명 세션 위에 로그인을 얹는다.
 *
 * <p>{@link #createSession}은 인증 방식(로컬/소셜)과 무관하게 세션을 발급하는 공통 로직이다.
 * 향후 카카오 OAuth 서비스에서 User 객체를 확보한 뒤 이 메서드를 직접 호출하면 된다.</p>
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
     * 로그인. username/password 검증 후 {@link #createSession}을 위임한다.
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

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        createSession(user, httpRequest, httpResponse);

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
     * 세션 발급 공통 로직. 인증 방식(로컬/소셜)과 무관하게 재사용된다.
     *
     * <ol>
     *   <li>User 객체에서 권한 목록을 결정한다.</li>
     *   <li>{@link UsernamePasswordAuthenticationToken}을 생성해 {@code SecurityContext}에 저장한다.</li>
     *   <li>{@code SecurityContextRepository.saveContext()}로 컨텍스트를 세션에 영속한다
     *       (이후 JSESSIONID 쿠키로 인증이 유지된다).</li>
     *   <li>현재 요청에서 익명 sessionId를 추출해 userId에 바인딩한다.</li>
     * </ol>
     *
     * <p>익명 sessionId를 현재 요청에서 뽑을 수 없는 흐름(예: 카카오 콜백)은
     * {@link #createSession(User, HttpServletRequest, HttpServletResponse, String)}으로
     * sessionId를 직접 넘긴다.</p>
     *
     * @param user         세션을 발급할 도우미 계정
     * @param httpRequest  현재 HTTP 요청 (익명 sessionId 추출 + 세션 저장에 사용)
     * @param httpResponse 현재 HTTP 응답 (SecurityContext 저장 시 Set-Cookie 헤더 작성에 사용)
     */
    public void createSession(User user,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        createSession(user, httpRequest, httpResponse, null);
    }

    /**
     * 세션 발급 공통 로직. 익명 sessionId를 호출 측에서 명시적으로 지정하는 오버로드.
     *
     * <p>카카오 콜백은 카카오 인가 서버가 보낸 요청이므로 {@code sessionId} 헤더가 없다.
     * 로그인 진입 시점에 HttpSession에 보관해둔 익명 sessionId를 꺼내 이 파라미터로 넘긴다.</p>
     *
     * @param anonymousSessionId 바인딩할 익명 세션 ID.
     *                           null/공백이면 현재 요청(헤더 → HttpSession 속성)에서 추출을 시도한다.
     */
    public void createSession(User user,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse,
                              String anonymousSessionId) {
        List<GrantedAuthority> authorities = resolveAuthorities(user);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);

        // 수동 인증 시 컨텍스트를 세션에 직접 저장하지 않으면 다음 요청에서 인증이 유지되지 않는다.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // 익명 sessionId ↔ userId 바인딩 (매칭·포인트가 sessionId로 계정 resolve 가능하도록)
        // 명시적으로 넘어온 값이 없으면 현재 요청에서 추출한다(기존 일반 로그인 동작).
        Optional<String> sessionIdToBind = (anonymousSessionId != null && !anonymousSessionId.isBlank())
                ? Optional.of(anonymousSessionId)
                : resolveAnonymousSessionId(httpRequest);

        sessionIdToBind.ifPresent(sessionId -> sessionAccountRepository.bind(sessionId, user.getId()));
    }

    /**
     * User 객체를 기준으로 권한 목록을 결정한다.
     *
     * <p>현재는 모든 도우미에게 {@code ROLE_HELPER}를 부여한다.
     * 향후 {@code UserRole} 필드를 추가할 경우 여기서 분기하면 된다.</p>
     */
    private List<GrantedAuthority> resolveAuthorities(User user) {
        return List.of(new SimpleGrantedAuthority("ROLE_HELPER"));
    }

    /**
     * 현재 요청에서 익명 sessionId를 추출한다.
     *
     * <p>우선순위: 요청 헤더 {@code sessionId} → HttpSession 속성 {@code sessionId}.</p>
     *
     * <p>카카오 로그인 진입 컨트롤러도 이 로직으로 익명 sessionId를 읽어
     * 콜백까지 왕복시키므로 public이다.</p>
     */
    public Optional<String> resolveAnonymousSessionId(HttpServletRequest request) {
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
