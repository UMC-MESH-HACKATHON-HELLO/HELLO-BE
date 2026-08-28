package com.mesh.hello.domain.policy.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mesh.hello.domain.policy.application.PolicyService;
import com.mesh.hello.domain.policy.domain.PolicyType;
import com.mesh.hello.domain.policy.dto.PolicyResponse;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.exception.GlobalExceptionHandler;
import com.mesh.hello.global.common.response.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    @Mock
    private PolicyService policyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PolicyController controller = new PolicyController(policyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("정상 조회 시 200과 정책 내용을 반환한다")
    void getPolicySuccess() throws Exception {
        PolicyResponse response = new PolicyResponse(
                PolicyType.TERMS, "이용약관", "내용", "1.0", LocalDate.of(2026, 8, 1));
        when(policyService.getPolicy("TERMS")).thenReturn(response);

        mockMvc.perform(get("/api/v1/policies/TERMS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.result.title").value("이용약관"));
    }

    @Test
    @DisplayName("잘못된 type이면 400을 반환한다")
    void getPolicyInvalidType() throws Exception {
        when(policyService.getPolicy("FOO")).thenThrow(new BusinessException(ErrorCode.INVALID_POLICY_TYPE));

        mockMvc.perform(get("/api/v1/policies/FOO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("등록된 정책이 없으면 404를 반환한다")
    void getPolicyNotFound() throws Exception {
        when(policyService.getPolicy("PRIVACY")).thenThrow(new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        mockMvc.perform(get("/api/v1/policies/PRIVACY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
