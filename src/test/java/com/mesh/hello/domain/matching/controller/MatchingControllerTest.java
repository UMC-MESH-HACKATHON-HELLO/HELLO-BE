package com.mesh.hello.domain.matching.controller;

import com.mesh.hello.domain.matching.application.MatchingService;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.exception.GlobalExceptionHandler;
import com.mesh.hello.global.common.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /help/stop의 파라미터 바인딩과 예외 → HTTP 상태 코드 매핑을 검증한다.
 * Spring 컨텍스트를 띄우지 않고 standalone MockMvc + 실제 {@link GlobalExceptionHandler}로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MatchingControllerTest {

    @Mock
    private MatchingService matchingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MatchingController controller = new MatchingController(matchingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("취소 성공 시 200과 sessionId 파라미터 바인딩을 확인한다")
    void stopSuccess() throws Exception {
        mockMvc.perform(post("/help/stop").param("sessionId", "helper-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("대기가 취소되었습니다."));

        verify(matchingService).stopHelperWaiting("helper-1");
    }

    @Test
    @DisplayName("대기열에 없는 sessionId로 취소를 시도하면 404를 반환한다")
    void stopNotFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.NOT_FOUND))
                .when(matchingService).stopHelperWaiting("stranger");

        mockMvc.perform(post("/help/stop").param("sessionId", "stranger"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("이미 통화 중인 sessionId로 취소를 시도하면 409를 반환한다")
    void stopAlreadyInCall() throws Exception {
        doThrow(new BusinessException(ErrorCode.ALREADY_IN_CALL))
                .when(matchingService).stopHelperWaiting("helper-1");

        mockMvc.perform(post("/help/stop").param("sessionId", "helper-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }
}
