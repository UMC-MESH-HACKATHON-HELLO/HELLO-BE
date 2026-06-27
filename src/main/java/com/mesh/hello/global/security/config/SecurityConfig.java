package com.mesh.hello.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // Spring Security를 활성화하기 위한 필수 어노테이션
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API 환경이므로 CSRF 보안은 끕니다. (켜두면 POST 요청 시 토큰이 필요해서 막힙니다)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. HTTP 요청에 대한 권한 설정 (중요★)
                .authorizeHttpRequests(auth -> auth
                        // 세션 생성 및 로그아웃 API는 로그인 안 한 상태에서도 접근할 수 있게 열어둡니다.
                        .requestMatchers("/api/session").permitAll()

                        // (선택 사항) 나중에 설정할 익명 권한별 접근 제한 예시
                        // .requestMatchers("/api/seller/**").hasRole("GUEST_SELLER")
                        // .requestMatchers("/api/buyer/**").hasRole("GUEST_BUYER")

                        // 그 외의 모든 요청은 일단 허용 (프로젝트 상황에 따라 .authenticated() 등으로 변경 가능)
                        .anyRequest().permitAll()
                )

                // 3. 기본 익명 사용자(Anonymous) 기능 설정
                .anonymous(anonymous -> anonymous
                        // 앞서 컨트롤러에서 토큰 만들 때 넣었던 "key_for_anonymous"와 일치시킵니다.
                        // 서버가 이 키를 가지고 익명 토큰의 위조 여부를 검증합니다.
                        .key("key_for_anonymous")
                        .principal("anonymousUser")
                );

        return http.build();
    }
}