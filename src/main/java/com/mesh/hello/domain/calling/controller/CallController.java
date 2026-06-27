package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.application.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final ReportService reportService;

    @PostMapping("/{roomId}/report")
    public ResponseEntity<Void> report(
            @PathVariable String roomId,
            @RequestParam String sessionId,
            @RequestParam(required = false) String reason,
            @RequestPart MultipartFile audio) {
        reportService.report(roomId, reason, audio, sessionId);
        return ResponseEntity.ok().build();
    }
}