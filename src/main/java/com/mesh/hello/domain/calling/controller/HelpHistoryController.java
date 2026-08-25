package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.application.HelpHistoryService;
import com.mesh.hello.domain.calling.dto.HelpHistoryResponse;
import com.mesh.hello.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "HelpHistory", description = "도우미 도움 기록 API")
@RestController
@RequestMapping("/api/v1/helper/helps")
@RequiredArgsConstructor
public class HelpHistoryController {

    private final HelpHistoryService helpHistoryService;

    @Operation(summary = "나의 도움 기록 조회",
            description = "도우미 본인이 수행한 도움 통화 기록을 카테고리별로 페이징 조회합니다. category 미지정 시 전체 조회.")
    @GetMapping
    public ApiResponse<HelpHistoryResponse> getHelpHistories(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "길찾기 | 스마트폰 | 키오스크 | 기타 (미지정 시 전체)")
            @RequestParam(required = false) String category) {
        return ApiResponse.ok("나의 도움 기록을 조회했습니다.",
                helpHistoryService.getHelpHistory(principal.getName(), page, size, category));
    }
}
