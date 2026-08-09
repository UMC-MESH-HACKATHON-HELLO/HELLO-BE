package com.mesh.hello.domain.calling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.mesh.hello.domain.calling.domain.CallSummary.CallCategory;
import static org.assertj.core.api.Assertions.assertThat;

class CallSummaryTest {

    @ParameterizedTest
    @CsvSource({
            "길찾기, ROAD_GUIDE",
            "스마트폰, SMARTPHONE",
            "키오스크, KIOSK",
            "기타, ETC"
    })
    @DisplayName("fromLabel - 한글 표시값을 대응하는 CallCategory로 매핑한다")
    void fromLabel_mapsKnownLabel(String label, CallCategory expected) {
        assertThat(CallCategory.fromLabel(label)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"등산", "ROAD_GUIDE", "", "길 찾기"})
    @DisplayName("fromLabel - 매칭되지 않는 값은 ETC로 fallback한다")
    void fromLabel_fallsBackToEtc(String label) {
        assertThat(CallCategory.fromLabel(label)).isEqualTo(CallCategory.ETC);
    }

    @Test
    @DisplayName("fromLabel - null이면 ETC로 fallback한다")
    void fromLabel_nullFallsBackToEtc() {
        assertThat(CallCategory.fromLabel(null)).isEqualTo(CallCategory.ETC);
    }
}