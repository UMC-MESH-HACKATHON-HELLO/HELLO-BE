package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CallSummaryPersistenceService {

    private final CallSummaryRepository callSummaryRepository;

    @Transactional
    public void completeSummary(CallSummary pending, String transcript, String summaryText) {
        if (pending != null) {
            pending.complete(transcript, summaryText);
            callSummaryRepository.save(pending);
        }
    }

    @Transactional
    public void failSummary(CallSummary pending, String transcript) {
        if (pending != null) {
            pending.fail(transcript);
            callSummaryRepository.save(pending);
        }
    }
}