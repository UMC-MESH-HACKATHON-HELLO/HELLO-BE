package com.mesh.hello.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 공용 {@link RestClient} 빈 설정.
 *
 * <p>baseUrl을 지정하지 않고 호출 측에서 절대 URL을 넘긴다.
 * 카카오 API처럼 엔드포인트가 여러 도메인(kauth.kakao.com, kapi.kakao.com)에 걸쳐 있어
 * 공용 인스턴스 하나로 통일하는 것이 더 단순하다.</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
