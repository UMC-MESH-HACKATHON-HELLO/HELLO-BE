package com.mesh.hello.domain.user.api;

import com.mesh.hello.domain.auth.repository.SessionAccountRepository;
import com.mesh.hello.domain.user.application.UserService;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.exception.GlobalExceptionHandler;
import com.mesh.hello.global.common.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link UserController#changePassword}의 파라미터 바인딩·검증·예외 매핑을 검증한다.
 *
 * <p>Spring 컨텍스트를 띄우지 않고 standalone MockMvc + 실제 {@link GlobalExceptionHandler}로 검증한다.
 * standaloneSetup에는 {@code WebMvcConfig}의 {@code /api/v1} 프리픽스가 적용되지 않으므로
 * {@code "/users/password"}로 호출한다.</p>
 *
 * <p>요청 본문은 텍스트 블록 JSON으로 직접 작성한다(ObjectMapper 미사용 — Jackson 3/2 혼선 회피).</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PATCH /users/password")
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private SessionAccountRepository sessionAccountRepository;

    private MockMvc mockMvc;

    private static final Principal PRINCIPAL = () -> "helperKim01";

    private static final String VALID_BODY = """
            { "currentPassword": "oldPw1234", "newPassword": "newPw5678" }
            """;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService, sessionAccountRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("성공 시 200 + 메시지, result 없음. 세션 바인딩 해제(unbind)까지 수행한다")
    void success() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String sessionId = session.getId();

        mockMvc.perform(patch("/users/password")
                        .principal(PRINCIPAL)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("비밀번호를 변경했습니다."))
                .andExpect(jsonPath("$.result").isEmpty());

        verify(userService).changePasswordByUsername(eq("helperKim01"), any());
        verify(sessionAccountRepository).unbind(sessionId);
    }

    @Test
    @DisplayName("새 비밀번호 형식 오류 → 400 INVALID_PASSWORD_FORMAT, 서비스 미호출")
    void newPasswordInvalidFormat() throws Exception {
        mockMvc.perform(patch("/users/password")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "oldPw1234", "newPassword": "short" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PASSWORD_FORMAT.getMessage()));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("현재 비밀번호 공백 + 새 비밀번호 형식 오류 동시 → PASSWORD_MISMATCH가 우선 노출된다(ordinal)")
    void bothInvalid_currentPasswordWins() throws Exception {
        mockMvc.perform(patch("/users/password")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "", "newPassword": "short" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(ErrorCode.PASSWORD_MISMATCH.getMessage()));

        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("서비스가 SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED를 던지면 400")
    void serviceThrowsSocial() throws Exception {
        willThrow(new BusinessException(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED))
                .given(userService).changePasswordByUsername(any(), any());

        mockMvc.perform(patch("/users/password")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED.getMessage()));
    }

    @Test
    @DisplayName("서비스가 PASSWORD_MISMATCH를 던지면 401이 아니라 400")
    void serviceThrowsPasswordMismatch() throws Exception {
        willThrow(new BusinessException(ErrorCode.PASSWORD_MISMATCH))
                .given(userService).changePasswordByUsername(any(), any());

        mockMvc.perform(patch("/users/password")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(ErrorCode.PASSWORD_MISMATCH.getMessage()));
    }
}
