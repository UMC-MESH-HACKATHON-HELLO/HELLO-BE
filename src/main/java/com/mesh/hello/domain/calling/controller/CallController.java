package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.application.CallRecordingService;
import com.mesh.hello.domain.calling.application.GeminiSummarizationService;
import com.mesh.hello.domain.calling.dto.CallSummaryResponse;
import com.mesh.hello.global.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/call")
@RequiredArgsConstructor
public class CallController {

    private final CallRecordingService callRecordingService;
    private final GeminiSummarizationService geminiSummarizationService;

    @PostMapping("/{roomId}/report")
    public ResponseEntity<Void> report(
            @PathVariable String roomId,
            @RequestParam String sessionId,
            @RequestPart MultipartFile audio) {
        callRecordingService.reportWithRecording(roomId, sessionId, audio);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/summary")
    public ApiResponse<CallSummaryResponse> getSummary(@PathVariable String roomId) {
        return ApiResponse.ok("요약을 조회했습니다.", geminiSummarizationService.getSummary(roomId));
    }
}
