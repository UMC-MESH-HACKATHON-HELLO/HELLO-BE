package com.mesh.hello.domain.calling.dto;

import com.mesh.hello.domain.calling.domain.CallSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallSummaryResponseTest {

    @Test
    @DisplayName("from - CallSummary의 category를 응답 DTO에 그대로 옮긴다")
    void from_mapsCategory() {
        CallSummary summary = new CallSummary("room-1", "helpee-1", "helper-1", 60);
        summary.complete("transcript", "요약 텍스트", CallSummary.CallCategory.SMARTPHONE);

        CallSummaryResponse response = CallSummaryResponse.from(summary);

        assertThat(response.category()).isEqualTo(CallSummary.CallCategory.SMARTPHONE);
        assertThat(response.category().getLabel()).isEqualTo("스마트폰");
    }
}