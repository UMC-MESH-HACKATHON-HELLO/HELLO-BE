package com.mesh.hello.domain.user.application;

import com.mesh.hello.domain.auth.application.KakaoOAuthService;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.dto.ChangePasswordRequest;
import com.mesh.hello.domain.user.enums.Provider;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link UserService#changePasswordByUsername} 단위 테스트.
 *
 * <p>검증 순서(소셜 차단 → 현재 비밀번호 → 기존과 동일) 각각의 분기와, 성공 시
 * 명시적 save 없이 도메인 객체만 변경되는지를 확인한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService.changePasswordByUsername")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private KakaoOAuthService kakaoOAuthService;

    @InjectMocks
    private UserService userService;

    private static final String USERNAME = "helperKim01";
    private static final String CURRENT_HASH = "$2a$10$currenthash";

    /** provider = LOCAL, password = CURRENT_HASH인 도우미 계정 픽스처. */
    private User localUser() {
        return User.createLocal(USERNAME, CURRENT_HASH, "김도우미", "helper@example.com", true);
    }

    @Test
    @DisplayName("성공 — password가 새 BCrypt 해시로 교체되고, 명시적 save는 호출하지 않는다(더티 체킹)")
    void success() {
        User user = localUser();
        given(userRepository.findByUsernameAndDeletedFalse(USERNAME)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPw1234", CURRENT_HASH)).willReturn(true);
        given(passwordEncoder.matches("newPw5678", CURRENT_HASH)).willReturn(false);
        given(passwordEncoder.encode("newPw5678")).willReturn("$2a$10$newhash");

        userService.changePasswordByUsername(USERNAME, new ChangePasswordRequest("oldPw1234", "newPw5678"));

        assertThat(user.getPassword()).isEqualTo("$2a$10$newhash");
        verify(userRepository, never()).save(any());
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("활성 계정이 없으면 UNAUTHORIZED")
        void accountNotFound() {
            given(userRepository.findByUsernameAndDeletedFalse("ghost")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePasswordByUsername("ghost",
                    new ChangePasswordRequest("oldPw1234", "newPw5678")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("소셜(카카오) 계정이면 SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED — matches를 호출조차 하지 않는다")
        void socialAccountRejected() {
            User kakao = User.ofSocial("kakao_12345678", "$2a$10$random", "카카오유저",
                    Provider.KAKAO, "12345678");
            given(userRepository.findByUsernameAndDeletedFalse("kakao_12345678")).willReturn(Optional.of(kakao));

            assertThatThrownBy(() -> userService.changePasswordByUsername("kakao_12345678",
                    new ChangePasswordRequest("oldPw1234", "newPw5678")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED);

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 PASSWORD_MISMATCH — password는 그대로, encode 미호출")
        void currentPasswordMismatch() {
            User user = localUser();
            given(userRepository.findByUsernameAndDeletedFalse(USERNAME)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPw999", CURRENT_HASH)).willReturn(false);

            assertThatThrownBy(() -> userService.changePasswordByUsername(USERNAME,
                    new ChangePasswordRequest("wrongPw999", "newPw5678")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

            assertThat(user.getPassword()).isEqualTo(CURRENT_HASH);
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("새 비밀번호가 현재와 동일하면 SAME_AS_CURRENT_PASSWORD — password는 그대로, encode 미호출")
        void sameAsCurrentPassword() {
            User user = localUser();
            given(userRepository.findByUsernameAndDeletedFalse(USERNAME)).willReturn(Optional.of(user));
            // 현재 비밀번호 확인(true) → 새 비밀번호도 현재 해시와 매칭(true)
            given(passwordEncoder.matches("oldPw1234", CURRENT_HASH)).willReturn(true);

            assertThatThrownBy(() -> userService.changePasswordByUsername(USERNAME,
                    new ChangePasswordRequest("oldPw1234", "oldPw1234")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SAME_AS_CURRENT_PASSWORD);

            assertThat(user.getPassword()).isEqualTo(CURRENT_HASH);
            verify(passwordEncoder, never()).encode(anyString());
        }
    }
}
