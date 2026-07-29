package com.mesh.hello.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 카카오 사용자 정보 API({@code /v2/user/me}) 응답 매핑.
 *
 * <p>현재 사용하는 필드는 {@code id}(카카오 회원번호)와
 * {@code properties.nickname}뿐이므로 나머지 최상위 필드는 생략한다.</p>
 *
 * <p>실제 카카오 응답 구조:
 * <pre>{@code
 * {
 *   "id": 1234567890,
 *   "properties": {
 *     "nickname": "홍길동"
 *   }
 * }
 * }</pre>
 * </p>
 *
 * <p>Jackson은 역직렬화 시 기본 생성자를 필요로 하므로,
 * 최상위 클래스는 일반 class로, 중첩 {@link Properties}는 record로 선언한다.
 * {@link #getNickname()} 편의 메서드로 nickname을 바로 꺼낼 수 있다.</p>
 */
public class KakaoUserInfoResDTO {

    /** 카카오 회원번호 (전 서비스 고유, 변경되지 않음). */
    @JsonProperty("id")
    private Long id;

    /** 카카오 프로필 정보 블록. */
    @JsonProperty("properties")
    private Properties properties;

    /** Jackson 역직렬화를 위한 기본 생성자. */
    public KakaoUserInfoResDTO() {}

    public Long getId() {
        return id;
    }

    /**
     * 카카오 닉네임을 반환한다.
     *
     * <p>properties 또는 nickname이 null인 경우를 방어적으로 처리한다.</p>
     *
     * @return 닉네임 문자열, 없으면 null
     */
    public String getNickname() {
        if (properties == null) {
            return null;
        }
        return properties.nickname();
    }

    /**
     * 카카오 {@code properties} 블록.
     *
     * <p>record로 선언해 불변성을 유지한다.
     * 현재는 nickname만 사용하며, 추후 profile_image 등이 필요하면 필드를 추가한다.</p>
     */
    public record Properties(

            @JsonProperty("nickname")
            String nickname
    ) {}
}
