package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.application.HelpHistoryService;
import com.mesh.hello.domain.calling.dto.HelpHistoryResponse;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.exception.GlobalExceptionHandler;
import com.mesh.hello.global.common.response.ErrorCode;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/helper/helps의 파라미터 바인딩과 예외 → HTTP 상태 코드 매핑을 검증한다.
 * Spring 컨텍스트를 띄우지 않고 standalone MockMvc + 실제 {@link GlobalExceptionHandler}로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class HelpHistoryControllerTest {

    @Mock
    private HelpHistoryService helpHistoryService;

    private MockMvc mockMvc;

    private final Principal principal = () -> "helper1";

    @BeforeEach
    void setUp() {
        HelpHistoryController controller = new HelpHistoryController(helpHistoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private RequestPostProcessor asPrincipal() {
        return request -> {
            request.setUserPrincipal(principal);
            return request;
        };
    }

    @Test
    @DisplayName("조회 성공 시 200과 page/size/category 파라미터 바인딩을 확인한다")
    void getHelpHistoriesSuccess() throws Exception {
        when(helpHistoryService.getHelpHistory("helper1", 1, 10, "키오스크"))
                .thenReturn(new HelpHistoryResponse(0, List.of()));

        mockMvc.perform(get("/api/v1/helper/helps")
                        .with(asPrincipal())
                        .param("page", "1")
                        .param("size", "10")
                        .param("category", "키오스크"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("나의 도움 기록을 조회했습니다."));

        verify(helpHistoryService).getHelpHistory("helper1", 1, 10, "키오스크");
    }

    @Test
    @DisplayName("page/size 미지정 시 기본값 0/20으로 바인딩된다")
    void getHelpHistoriesDefaultsPageSize() throws Exception {
        when(helpHistoryService.getHelpHistory(eq("helper1"), eq(0), eq(20), any()))
                .thenReturn(new HelpHistoryResponse(0, List.of()));

        mockMvc.perform(get("/api/v1/helper/helps").with(asPrincipal()))
                .andExpect(status().isOk());

        verify(helpHistoryService).getHelpHistory("helper1", 0, 20, null);
    }

    @Test
    @DisplayName("잘못된 page/size면 400을 반환한다")
    void getHelpHistoriesInvalidPaging() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_PAGING))
                .when(helpHistoryService).getHelpHistory("helper1", -1, 20, null);

        mockMvc.perform(get("/api/v1/helper/helps").with(asPrincipal()).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("알 수 없는 category면 400을 반환한다")
    void getHelpHistoriesInvalidCategory() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_CATEGORY))
                .when(helpHistoryService).getHelpHistory("helper1", 0, 20, "등산");

        mockMvc.perform(get("/api/v1/helper/helps").with(asPrincipal()).param("category", "등산"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
