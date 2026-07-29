package com.mesh.hello.domain.user.repository;

import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.enums.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 소셜 로그인 시 기존 가입 여부 확인.
     *
     * @param provider   OAuth 제공자 (예: {@code Provider.KAKAO})
     * @param providerId OAuth 제공자의 사용자 ID (예: 카카오 회원번호)
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
