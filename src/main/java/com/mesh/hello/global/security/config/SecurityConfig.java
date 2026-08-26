package com.mesh.hello.global.security.config;

import com.mesh.hello.global.security.RestAuthenticationEntryPoint;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 세션 기반 인증 설정(JWT 아님). 익명 세션 위에 도우미 로그인을 얹는다.
 *
 * <ul>
 *   <li>무인증이 핵심 가치 → 어르신/익명 플로우는 막지 않고 도우미 전용 경로만 보호.</li>
 *   <li>세션 IF_REQUIRED. 로그인 시 SecurityContext를 세션에 저장 → JSESSIONID로 인증 유지.</li>
 *   <li>{@code /api/v1/helper/**} authenticated, 그 외 permitAll.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // MVP: JSON API라 CSRF 비활성화. 세션 쿠키 기반이므로 실서비스 전 반드시 재검토.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        // 로그인/가입/로그아웃
                        .requestMatchers("/api/v1/login", "/api/v1/signup", "/api/v1/logout").permitAll()
                        // 카카오 OAuth (인가코드 요청 리다이렉트, 콜백)
                        .requestMatchers("/api/v1/oauth/kakao/**").permitAll()
                        // 익명 세션 발급(CM102)
                        .requestMatchers("/api/v1/session").permitAll()
                        // 익명 WebSocket 핸드셰이크/SockJS (어르신·익명 매칭 플로우)
                        .requestMatchers("/api/v1/ws/**").permitAll()
                        // Swagger UI
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/api-docs/**").permitAll()
                        // 정적/데모 리소스
                        .requestMatchers("/", "/error", "/favicon.ico",
                                "/stomp-test.html", "/livekit-client.umd.min.js", "/h2-console/**").permitAll()
                        // 도우미 전용 (대기 시작/종료·포인트 조회 등은 여기에 추가)
                        .requestMatchers("/helper/**").authenticated()
                        // 도우미 포인트 내역 조회
                        .requestMatchers("/api/v1/helper/points/**").authenticated()
                        .requestMatchers("/api/v1/helper/**").authenticated()
                        // 탈퇴 등 사용자 본인 정보 수정 (로그인 필수)
                        .requestMatchers("/api/v1/users/**").authenticated()
                        // 그 외는 무인증 허용 — 어르신/익명 플로우가 막히면 안 됨
                        .anyRequest().permitAll()
                )
                // 팀원 설정 보존: 익명 사용자 기능
                .anonymous(anonymous -> anonymous
                        .key("key_for_anonymous")
                        .principal("anonymousUser")
                )
                .exceptionHandling(eh -> eh.authenticationEntryPoint(restAuthenticationEntryPoint))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())          // 커스텀 /logout 사용
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())); // H2 콘솔용

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "https://hello.sublumen.xyz",
                "https://hello-fe-bay.vercel.app",
                "https://hello-fe-git-main-jungbin.vercel.app"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
