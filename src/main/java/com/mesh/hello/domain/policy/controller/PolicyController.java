package com.mesh.hello.domain.policy.controller;

import com.mesh.hello.domain.policy.application.PolicyService;
import com.mesh.hello.domain.policy.dto.PolicyResponse;
import com.mesh.hello.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Policy", description = "약관/개인정보처리방침 조회 API")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @Operation(summary = "정책 조회", description = "type(TERMS, PRIVACY)에 해당하는 정책 문서를 조회합니다.")
    @GetMapping("/{type}")
    public ApiResponse<PolicyResponse> getPolicy(@PathVariable String type) {
        return ApiResponse.ok("정책을 조회했습니다.", policyService.getPolicy(type));
    }
}
