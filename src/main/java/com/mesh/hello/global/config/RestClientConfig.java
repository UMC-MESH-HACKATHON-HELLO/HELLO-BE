package com.mesh.hello.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 공용 {@link RestClient} 빈 설정.
 *
 * <p>baseUrl을 지정하지 않고 호출 측에서 절대 URL을 넘긴다.
 * 카카오 API처럼 엔드포인트가 여러 도메인(kauth.kakao.com, kapi.kakao.com)에 걸쳐 있어
 * 공용 인스턴스 하나로 통일하는 것이 더 단순하다.</p>
 *
 * <p>서블릿 스레드가 원격 엔드포인트 지연으로 무한 블로킹되지 않도록
 * connect/read 타임아웃을 명시적으로 지정한다.
 * 기본값은 각 5초이며 {@code rest-client.timeout.*} 프로퍼티로 재정의할 수 있다.</p>
 */
@Configuration
public class RestClientConfig {

    @Value("${rest-client.timeout.connect-millis:5000}")
    private int connectMillis;

    @Value("${rest-client.timeout.read-millis:5000}")
    private int readMillis;

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMillis);
        factory.setReadTimeout(readMillis);

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
