package com.mesh.hello.domain.calling.controller;

import com.mesh.hello.domain.calling.dto.CallSummaryResponse;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallSummaryController {

    private final CallSummaryRepository summaryRepository;

    @GetMapping("/summary/{roomId}")
    public ResponseEntity<CallSummaryResponse> getSummary(@PathVariable String roomId) {
        return summaryRepository.findByRoomId(roomId)
                .map(s -> ResponseEntity.ok(CallSummaryResponse.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/summaries")
    public ResponseEntity<List<CallSummaryResponse>> getAllSummaries() {
        return ResponseEntity.ok(
                summaryRepository.findAll().stream()
                        .map(CallSummaryResponse::from)
                        .toList()
        );
    }
}