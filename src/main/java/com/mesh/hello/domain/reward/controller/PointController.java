package com.mesh.hello.domain.reward.controller;

import com.mesh.hello.domain.reward.application.PointService;
import com.mesh.hello.domain.reward.dto.PointHistoryResponse;
import com.mesh.hello.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Point", description = "도우미 포인트 API")
@RestController
@RequestMapping("/api/v1/helper/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @Operation(summary = "포인트 내역 조회", description = "도우미 본인의 포인트 적립/차감 내역과 보유 포인트 총합을 조회합니다.")
    @GetMapping
    public ApiResponse<PointHistoryResponse> getPointHistories(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("포인트 내역을 조회했습니다.", pointService.getPointHistory(principal.getName(), page, size));
    }
}
