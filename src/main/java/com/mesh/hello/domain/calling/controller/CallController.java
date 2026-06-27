package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.application.CallRecordingService;
import com.mesh.hello.domain.calling.application.TranscriptBufferService;
import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallRecordingService callRecordingService;
    private final CallSummaryRepository callSummaryRepository;
    private final TranscriptBufferService transcriptBufferService;

    @PostMapping("/{roomId}/report")
    public ResponseEntity<Void> report(
            @PathVariable String roomId,
            @RequestParam String sessionId,
            @RequestPart MultipartFile audio) {
        callRecordingService.reportWithRecording(roomId, sessionId, audio);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{roomId}/transcript")
    public ResponseEntity<String> getTranscript(@PathVariable String roomId) {
        return ResponseEntity.ok(transcriptBufferService.peek(roomId));
    }

    @GetMapping("/summaries")
    public ResponseEntity<List<CallSummary>> getAllSummaries() {
        return ResponseEntity.ok(callSummaryRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/{roomId}/summary")
    public ResponseEntity<CallSummary> getSummaryByRoomId(@PathVariable String roomId) {
        return callSummaryRepository.findTopByRoomIdOrderByCreatedAtDesc(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
