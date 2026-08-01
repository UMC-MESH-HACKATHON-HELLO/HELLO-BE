package com.mesh.hello.domain.auth.application;

import com.mesh.hello.domain.auth.dto.KakaoUserInfoResDTO;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.enums.Provider;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import com.mesh.hello.global.config.KakaoOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link KakaoOAuthService} 단위 테스트.
 *
 * <p>카카오 HTTP 호출 메서드(requestToken, requestUserInfo)는 RestClient 목킹이
 * 복잡하므로 이 테스트에서는 제외하고, 순수 비즈니스 로직인
 * {@link KakaoOAuthService#loginOrSignup}과 {@link KakaoOAuthService#buildAuthorizeUrl}만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class KakaoOAuthServiceTest {

    @Mock
    private KakaoOAuthProperties kakaoProps;

    @Mock
    private RestClient restClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private KakaoOAuthService kakaoOAuthService;

    // -----------------------------------------------------------------------
    // buildAuthorizeUrl
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("buildAuthorizeUrl - 카카오 인가 URL 생성")
    class BuildAuthorizeUrlTest {

        @BeforeEach
        void setUp() {
            given(kakaoProps.authUri()).willReturn("https://kauth.kakao.com/oauth/authorize");
            given(kakaoProps.clientId()).willReturn("test-client-id");
            given(kakaoProps.redirectUri()).willReturn("http://localhost:8080/api/v1/oauth/kakao/callback");
        }

        @Test
        @DisplayName("필수 쿼리 파라미터가 모두 포함된 URL을 반환한다")
        void containsAllRequiredParams() {
            String url = kakaoOAuthService.buildAuthorizeUrl("test-state-value");

            assertThat(url)
                    .contains("https://kauth.kakao.com/oauth/authorize")
                    .contains("client_id=test-client-id")
                    .contains("redirect_uri=")
                    .contains("response_type=code")
                    .contains("state=test-state-value")
                    .contains("scope=profile_nickname");
        }

        @Test
        @DisplayName("전달받은 state 값이 URL에 그대로 포함된다")
        void stateParamMatchesInput() {
            String state = "random-csrf-token-xyz";

            String url = kakaoOAuthService.buildAuthorizeUrl(state);

            assertThat(url).contains("state=" + state);
        }
    }

    // -----------------------------------------------------------------------
    // loginOrSignup
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("loginOrSignup - 로그인/회원가입 분기")
    class LoginOrSignupTest {

        private KakaoUserInfoResDTO makeUserInfo(Long kakaoId, String nickname) {
            KakaoUserInfoResDTO dto = new KakaoUserInfoResDTO();
            // 리플렉션으로 private 필드를 설정하는 대신, 카카오 응답을 흉내 낸 서브클래스 방식 사용
            // → Jackson 역직렬화 없이 테스트용 팩토리 메서드가 없으므로 직접 setter 대신
            //    스파이 또는 별도 인스턴스 생성 방식을 쓴다.
            // KakaoUserInfoResDTO는 기본 생성자 + public getter만 있으므로
            // 여기서는 익명 서브클래스로 오버라이드한다.
            return new KakaoUserInfoResDTO() {
                @Override
                public Long getId() {
                    return kakaoId;
                }

                @Override
                public String getNickname() {
                    return nickname;
                }
            };
        }

        @Test
        @DisplayName("기존 카카오 유저가 있으면 새로 저장하지 않고 기존 유저를 반환한다")
        void existingUser_returnsWithoutSaving() {
            // given
            Long kakaoId = 1234567890L;
            String providerId = String.valueOf(kakaoId);

            User existingUser = User.ofSocial(
                    "kakao_" + providerId,
                    "encoded-password",
                    "기존닉네임",
                    Provider.KAKAO,
                    providerId
            );
            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId))
                    .willReturn(Optional.of(existingUser));

            // when
            User result = kakaoOAuthService.loginOrSignup(makeUserInfo(kakaoId, "기존닉네임"));

            // then
            assertThat(result).isSameAs(existingUser);
            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("신규 유저면 User.ofSocial로 생성하고 저장한다")
        void newUser_createsAndSaves() {
            // given
            Long kakaoId = 9999999999L;
            String providerId = String.valueOf(kakaoId);
            String kakaoNickname = "새유저닉네임";

            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId))
                    .willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("bcrypt-hashed-password");

            User savedUser = User.ofSocial(
                    "kakao_" + providerId,
                    "bcrypt-hashed-password",
                    kakaoNickname,
                    Provider.KAKAO,
                    providerId
            );
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // when
            User result = kakaoOAuthService.loginOrSignup(makeUserInfo(kakaoId, kakaoNickname));

            // then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User captured = userCaptor.getValue();
            assertThat(captured.getUsername()).isEqualTo("kakao_" + providerId);
            assertThat(captured.getNickname()).isEqualTo(kakaoNickname);
            assertThat(captured.getProvider()).isEqualTo(Provider.KAKAO);
            assertThat(captured.getProviderId()).isEqualTo(providerId);
            assertThat(result).isSameAs(savedUser);
        }

        @Test
        @DisplayName("신규 유저 생성 시 username은 'kakao_' + kakaoId 형식이다")
        void newUser_usernamePattern() {
            // given
            Long kakaoId = 42L;
            String providerId = String.valueOf(kakaoId);

            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId))
                    .willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            User result = kakaoOAuthService.loginOrSignup(makeUserInfo(kakaoId, "닉"));

            // then
            assertThat(result.getUsername()).isEqualTo("kakao_42");
        }

        @Test
        @DisplayName("신규 유저 생성 시 BCrypt로 인코딩된 랜덤 패스워드를 저장한다")
        void newUser_passwordIsEncoded() {
            // given
            Long kakaoId = 77L;
            String providerId = String.valueOf(kakaoId);

            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId))
                    .willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("$2a$10$encodedHash");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            User result = kakaoOAuthService.loginOrSignup(makeUserInfo(kakaoId, "닉"));

            // then
            verify(passwordEncoder).encode(anyString());
            assertThat(result.getPassword()).isEqualTo("$2a$10$encodedHash");
        }

        @Test
        @DisplayName("카카오 닉네임이 null이어도 유저를 정상 생성한다")
        void newUser_nullNicknameAllowed() {
            // given
            Long kakaoId = 55L;
            String providerId = String.valueOf(kakaoId);

            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId))
                    .willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            // when
            User result = kakaoOAuthService.loginOrSignup(makeUserInfo(kakaoId, null));

            // then
            assertThat(result.getNickname()).isNull();
        }

        @Test
        @DisplayName("username을 이미 로컬 계정이 선점했다면(kakao_ 접두사 스쿼팅) 저장하지 않고 명확한 에러를 던진다")
        void newUser_usernameSquattedByLocalAccount_throwsConflict() {
            // given: 신규 카카오 유저(=아직 provider/providerId로는 매칭되지 않음)지만,
            // 로컬 회원가입이 (지금은 차단되었지만) 과거에 kakao_<providerId> 형태의
            // username을 이미 선점해둔 상황을 시뮬레이션한다.
            Long kakaoId = 12345678L;
            String providerId = String.valueOf(kakaoId);
            String squattedUsername = KakaoOAuthService.USERNAME_PREFIX + providerId;

            given(userRepository.findByProviderAndProviderId(Provider.KAKAO, providerId))
                    .willReturn(Optional.empty());
            given(userRepository.existsByUsername(squattedUsername)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> kakaoOAuthService.loginOrSignup(makeUserInfo(kakaoId, "실제카카오유저")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.KAKAO_USERNAME_CONFLICT);

            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(anyString());
        }
    }
}
