package com.mesh.hello.domain.calling.application;

import com.mesh.hello.domain.calling.domain.CallSummary;
import com.mesh.hello.domain.calling.repository.CallSummaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CallSummaryPersistenceServiceTest {

    @Mock
    private CallSummaryRepository callSummaryRepository;

    private CallSummaryPersistenceService persistenceService;

    @Test
    @DisplayName("completeSummary - category를 포함해 요약을 완료 처리하고 저장한다")
    void completeSummary_savesWithCategory() {
        persistenceService = new CallSummaryPersistenceService(callSummaryRepository);
        CallSummary pending = new CallSummary("room-1", "helpee-1", "helper-1", 1L, 90);

        persistenceService.completeSummary(pending, "transcript", "summary text", CallSummary.CallCategory.KIOSK);

        ArgumentCaptor<CallSummary> captor = ArgumentCaptor.forClass(CallSummary.class);
        verify(callSummaryRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo(CallSummary.CallCategory.KIOSK);
        assertThat(captor.getValue().getSummary()).isEqualTo("summary text");
        assertThat(captor.getValue().getStatus()).isEqualTo(CallSummary.SummaryStatus.COMPLETED);
    }
}