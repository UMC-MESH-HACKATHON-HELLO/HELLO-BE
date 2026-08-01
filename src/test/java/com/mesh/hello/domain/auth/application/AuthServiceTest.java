package com.mesh.hello.domain.auth.application;

import com.mesh.hello.domain.auth.dto.SignupRequest;
import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AuthService} 단위 테스트.
 *
 * <p>회귀 시나리오: 로컬 회원가입에서 {@code kakao_} 접두사(카카오 소셜 유저의
 * 결정적 username 생성 규칙, {@link KakaoOAuthService#USERNAME_PREFIX})를 예약어로
 * 차단하지 않으면, 공격자가 실제 카카오 유저보다 먼저 {@code kakao_<providerId>}
 * username을 선점해 해당 카카오 유저의 로그인을 막을 수 있다(username 전역 유니크 제약).</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionAccountRepository sessionAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("signup - kakao_ 접두사 예약어 차단")
    class ReservedPrefixTest {

        @Test
        @DisplayName("username이 kakao_로 시작하면 RESERVED_USERNAME_PREFIX 에러를 던지고 저장하지 않는다")
        void kakaoPrefixUsername_isRejected() {
            // given: 실제 카카오 유저(providerId=12345678)가 존재하기 전에
            // 공격자가 동일한 username을 로컬 가입으로 선점하려는 시도
            SignupRequest request = new SignupRequest("kakao_12345678", "password123", "닉네임");

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVED_USERNAME_PREFIX);

            verify(userRepository, never()).existsByUsername(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("username이 kakao_로 시작하면 존재하지 않는 providerId라도 거부한다")
        void kakaoPrefixUsername_rejectedEvenIfProviderIdNotYetTaken() {
            SignupRequest request = new SignupRequest("kakao_999999999999", "password123", null);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVED_USERNAME_PREFIX);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("signup - username 형식 검증")
    class FormatValidationTest {

        @ParameterizedTest
        @ValueSource(strings = {"ab", "user name", "유저이름", "user!!", ""})
        @DisplayName("허용되지 않는 형식의 username은 INVALID_USERNAME_FORMAT 에러를 던진다")
        void invalidFormatUsername_isRejected(String invalidUsername) {
            SignupRequest request = new SignupRequest(invalidUsername, "password123", "닉네임");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_USERNAME_FORMAT);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("signup - 정상 가입")
    class SuccessTest {

        @Test
        @DisplayName("형식이 유효하고 중복되지 않은 username이면 가입에 성공한다")
        void validUsername_signsUpSuccessfully() {
            SignupRequest request = new SignupRequest("normal_user", "password123", "닉네임");

            given(userRepository.existsByUsername("normal_user")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("bcrypt-hash");
            given(userRepository.save(any(User.class))).willAnswer(inv -> {
                User user = inv.getArgument(0);
                return user;
            });

            authService.signup(request);

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("이미 존재하는 username이면 DUPLICATE_USERNAME 에러를 던진다")
        void duplicateUsername_isRejected() {
            SignupRequest request = new SignupRequest("normal_user", "password123", "닉네임");
            given(userRepository.existsByUsername("normal_user")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);

            verify(userRepository, never()).save(any(User.class));
        }
    }
}
