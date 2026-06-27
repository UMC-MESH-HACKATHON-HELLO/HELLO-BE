package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.application.CallRecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallRecordingService callRecordingService;

    @PostMapping("/{roomId}/report")
    public ResponseEntity<Void> report(
            @PathVariable String roomId,
            @RequestParam String sessionId,
            @RequestPart MultipartFile audio) {
        callRecordingService.reportWithRecording(roomId, sessionId, audio);
        return ResponseEntity.ok().build();
    }
}
