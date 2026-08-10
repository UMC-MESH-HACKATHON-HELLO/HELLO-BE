package com.mesh.hello.domain.user.repository;

import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.enums.Provider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 보유 포인트를 원자적으로 증감한다. 동시 적립 시 갱신 유실을 막기 위해 find-and-save 대신 사용한다. */
    @Modifying
    @Query("UPDATE User u SET u.points = u.points + :amount WHERE u.id = :id")
    int addPoints(@Param("id") Long id, @Param("amount") long amount);

    /**
     * 소셜 로그인 시 기존 가입 여부 확인.
     *
     * @param provider   OAuth 제공자 (예: {@code Provider.KAKAO})
     * @param providerId OAuth 제공자의 사용자 ID (예: 카카오 회원번호)
     */
    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
