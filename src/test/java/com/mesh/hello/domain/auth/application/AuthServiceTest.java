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
 *
 * <p>대소문자 변형 회귀 시나리오: MySQL 기본 collation(utf8mb4_0900_ai_ci)은
 * case-insensitive이므로 {@code KAKAO_123}과 {@code kakao_123}이 동일한 유니크 자리를 차지한다.
 * 비교를 {@code startsWith}(case-sensitive)로만 하면 {@code KAKAO_} · {@code KaKaO_} 등
 * 대소문자 변형이 검증을 통과해 선점 공격이 가능해진다.</p>
 *
 * <p>{@code deleted_} 접두사 회귀 시나리오: {@link com.mesh.hello.domain.user.domain.User#withdraw}가
 * 탈퇴 계정의 username을 {@code deleted_<id>}({@link com.mesh.hello.domain.user.domain.User#WITHDRAWN_USERNAME_PREFIX})로
 * 익명화한다. 로컬 가입에서 이 접두사를 막지 않으면 누군가 {@code deleted_<id>}를 선점해
 * 해당 id 사용자의 탈퇴를 유니크 제약 위반으로 롤백시킬 수 있다(카카오 unlink는 익명화보다
 * 먼저 호출되므로, 카카오 연결은 끊겼는데 탈퇴는 실패한 불일치 상태가 된다).</p>
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

        /**
         * 대소문자 변형 회귀 테스트.
         *
         * <p>MySQL utf8mb4_0900_ai_ci(기본 collation)에서는 {@code KAKAO_123}이
         * {@code kakao_123}과 동일한 유니크 자리를 차지한다.
         * 컬럼에 collation이 명시되지 않으면 DB 환경마다 결과가 달라지므로
         * 애플리케이션 레이어에서 대소문자 무관하게 차단해야 한다.</p>
         */
        @ParameterizedTest(name = "[{index}] username=\"{0}\"")
        @ValueSource(strings = {
                "KAKAO_12345678",   // 전체 대문자
                "KaKaO_12345678",   // 혼합 대소문자
                "Kakao_12345678",   // 첫 글자만 대문자
                "KAKAO_",           // 접두사만
                "kAkAo_99999",      // 무작위 대소문자
        })
        @DisplayName("kakao_ 접두사의 대소문자 변형도 RESERVED_USERNAME_PREFIX 에러를 던진다")
        void kakaoPrefixCaseVariants_areRejected(String usernameVariant) {
            SignupRequest request = new SignupRequest(usernameVariant, "password123", "닉네임");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVED_USERNAME_PREFIX);

            verify(userRepository, never()).existsByUsername(anyString());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("signup - deleted_ 접두사 예약어 차단")
    class WithdrawnPrefixTest {

        @Test
        @DisplayName("username이 deleted_로 시작하면 RESERVED_USERNAME_PREFIX 에러를 던지고 저장하지 않는다")
        void deletedPrefixUsername_isRejected() {
            // given: 탈퇴 시 username은 deleted_<id>로 익명화된다(User#withdraw).
            // 공격자가 deleted_42를 선점하면 id=42 사용자의 탈퇴가 유니크 제약 위반으로 롤백된다.
            SignupRequest request = new SignupRequest("deleted_42", "password123", "닉네임");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVED_USERNAME_PREFIX);

            verify(userRepository, never()).existsByUsername(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @ParameterizedTest(name = "[{index}] username=\"{0}\"")
        @ValueSource(strings = {
                "DELETED_42",   // 전체 대문자
                "Deleted_42",   // 첫 글자만 대문자
                "DeLeTeD_99",   // 혼합 대소문자
                "deleted_",     // 접두사만
        })
        @DisplayName("deleted_ 접두사의 대소문자 변형도 RESERVED_USERNAME_PREFIX 에러를 던진다")
        void deletedPrefixCaseVariants_areRejected(String usernameVariant) {
            SignupRequest request = new SignupRequest(usernameVariant, "password123", "닉네임");

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVED_USERNAME_PREFIX);

            verify(userRepository, never()).existsByUsername(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("언더스코어 없는 'deleted'는 예약어가 아니므로 중복 검사 단계까지 진행된다")
        void plainDeletedWithoutUnderscore_isNotReserved() {
            // deleted_<id> 형태만 시스템이 생성하므로, 언더스코어 없는 'deleted'는 충돌 불가 → 차단하지 않는다.
            SignupRequest request = new SignupRequest("deleted", "password123", "닉네임");
            given(userRepository.existsByUsername("deleted")).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_USERNAME);
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
