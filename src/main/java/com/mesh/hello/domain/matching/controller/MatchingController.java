package com.mesh.hello.domain.matching.controller;

import com.mesh.hello.domain.matching.application.MatchingService;
import com.mesh.hello.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭 대기열 관련 REST 엔드포인트.
 *
 * <p>대기열 등록/매칭 자체는 STOMP({@link SignalingController})의 {@code Principal}(sessionId)
 * 기준으로 동작하지만, 이 컨트롤러는 REST라 별도 인증 체계가 없어 {@code sessionId}를
 * 쿼리 파라미터로 받는다. {@code /helper/**}(로그인 인증 필요)와 경로가 겹치지 않도록
 * STOMP 목적지와 동일하게 {@code /help} 프리픽스를 쓴다.</p>
 */
@RestController
@RequestMapping("/help")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    /** 도우미가 스스로 대기열 등록을 취소한다. */
    @PostMapping("/stop")
    public ApiResponse<Void> stopHelperWaiting(@RequestParam String sessionId) {
        matchingService.stopHelperWaiting(sessionId);
        return ApiResponse.ok("대기가 취소되었습니다.", null);
    }
}