package com.mesh.hello.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesh.hello.domain.auth.application.AuthService;
import com.mesh.hello.domain.auth.dto.SignupRequest;
import com.mesh.hello.global.common.exception.GlobalExceptionHandler;
import com.mesh.hello.global.common.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AuthController} 단위 테스트 — username 형식 검증.
 *
 * <p>username 형식 검증은 {@link SignupRequest}의 Bean Validation({@code @Pattern})으로
 * 컨트롤러 레이어에서 사전 차단된다. 서비스({@link AuthService})가 아니라
 * 컨트롤러 레이어에서 {@link ErrorCode#INVALID_USERNAME_FORMAT}이 반환되어야 함을 검증한다.</p>
 *
 * <p>Bean Validation → {@link org.springframework.web.bind.MethodArgumentNotValidException}
 * → {@link GlobalExceptionHandler} → HTTP 400 흐름을 end-to-end로 확인한다.</p>
 *
 * <h2>왜 AuthServiceTest가 아닌 여기에 있는가</h2>
 * <p>이전 {@code AuthServiceTest.FormatValidationTest}는 서비스를 직접 호출해
 * {@code INVALID_USERNAME_FORMAT} 에러를 기대했다. 그러나 {@link AuthService#signup}은
 * 형식 검증 로직을 갖지 않는다(Javadoc 명시). 검증은 {@code @Valid} + Bean Validation이
 * 컨트롤러 레이어에서 수행하므로, MockMvc 슬라이스에서만 올바르게 커버된다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 유효한 SignupRequest JSON을 생성한다. username만 교체해서 테스트한다.
     * password, passwordConfirm, email, privacyAgreed는 Bean Validation 통과 조건을 맞춘다.
     */
    private String signupJson(String username) throws Exception {
        SignupRequest req = new SignupRequest(
                username,
                "password1",
                "password1",
                "test@example.com",
                "닉네임",
                true
        );
        return objectMapper.writeValueAsString(req);
    }

    // -----------------------------------------------------------------------
    // username 형식 검증 — [이동] from AuthServiceTest.FormatValidationTest
    //
    // 삭제가 아니라 이동이다.
    // Bean Validation(@Pattern)은 컨트롤러 레이어(@Valid)에서 트리거되므로
    // 서비스 단위 테스트에서는 해당 검증이 일어나지 않는다.
    // 실제 검증 흐름(HTTP 요청 → @Valid → MethodArgumentNotValidException → GlobalExceptionHandler)을
    // 커버하려면 MockMvc 기반 컨트롤러 테스트가 필요하다.
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /signup - username 형식 검증")
    class UsernameFormatValidationTest {

        @ParameterizedTest(name = "[{index}] username=\"{0}\"")
        @ValueSource(strings = {
                "ab",           // 2자 — 최솟값(3자) 미달
                "user name",    // 공백 포함
                "유저이름",      // 한글(영문/숫자/언더스코어 외 문자)
                "user!!",       // 특수문자 포함
        })
        @DisplayName("허용되지 않는 형식의 username은 400 INVALID_USERNAME_FORMAT을 반환한다")
        void invalidFormatUsername_returns400(String invalidUsername) throws Exception {
            mockMvc.perform(post("/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupJson(invalidUsername)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_USERNAME_FORMAT.getCode()));
        }

        @ParameterizedTest(name = "[{index}] username=\"{0}\"")
        @ValueSource(strings = {
                "abc",          // 정확히 3자
                "valid_user",   // 언더스코어 포함
                "User123",      // 대문자 포함
                "a1b2c3d4e5f6", // 12자
        })
        @DisplayName("유효한 username은 Bean Validation을 통과해 서비스까지 도달한다")
        void validFormatUsername_passesValidation(String validUsername) throws Exception {
            given(authService.signup(any(SignupRequest.class))).willReturn(1L);

            mockMvc.perform(post("/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupJson(validUsername)))
                    .andExpect(status().isOk());
        }
    }
}
