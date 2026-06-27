package com.mesh.hello.domain.communication.controller;

import com.mesh.hello.domain.communication.application.CallService;
import com.mesh.hello.domain.communication.domain.CallSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/call")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    @PostMapping("/create")
    public ResponseEntity<CallSession> createCall(
            @RequestParam String helpeeId,
            @RequestParam String helperId) {

        CallSession session = callService.createCall(helpeeId, helperId);
        return ResponseEntity.ok(session);
    }
}