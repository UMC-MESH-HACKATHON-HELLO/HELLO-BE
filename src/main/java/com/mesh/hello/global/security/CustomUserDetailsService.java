package com.mesh.hello.global.security;

import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 도우미 계정 로딩. AuthenticationManager가 비밀번호 검증에 사용한다.
 *
 * <p>탈퇴 처리된 계정({@code deleted = true})은 조회 자체가 되지 않으므로
 * {@code UsernameNotFoundException}이 발생해 인증이 차단된다.
 * 탈퇴 시 username이 {@code deleted_{id}}로 익명화·비밀번호가 랜덤 BCrypt 해시로
 * 교체되어 우연히 막히던 기존 동작을 쿼리 레벨에서 명시적으로 보장한다.</p>
 *
 * <p>탈퇴 계정과 미존재 계정 모두 동일한 메시지를 반환해 계정 열거를 방지한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // deleted = true 인 계정은 쿼리 단에서 걸러진다 — 탈퇴/미존재 모두 동일 예외
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(new SimpleGrantedAuthority("ROLE_HELPER"))
                .build();
    }
}
