package com.portfolio.chungyak.web;

import com.portfolio.chungyak.service.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 푸터의 "데이터 최종 갱신" — 한국시간으로 보여주고, 수집 성공 기록이 없으면 줄 자체를 감춘다.
 */
class GlobalModelAttributesTest {

    private static GlobalModelAttributes withLastSuccess(Instant at) {
        SyncStatus status = mock(SyncStatus.class);
        when(status.getLastSuccessAt()).thenReturn(at);
        return new GlobalModelAttributes(status);
    }

    @Test
    @DisplayName("수집 성공 시각을 한국시간으로 변환해 보여준다")
    void formatsInKoreanTime() {
        // 2026-09-18 19:00 UTC = 2026-09-19 04:00 KST (수집 배치가 도는 새벽 4시)
        assertThat(withLastSuccess(Instant.parse("2026-09-18T19:00:00Z")).lastSyncAt())
                .isEqualTo("2026-09-19 04:00");
    }

    @Test
    @DisplayName("한 번도 성공한 수집이 없으면 빈 값 — 화면에서 줄을 감춘다")
    void emptyWhenNeverSynced() {
        assertThat(withLastSuccess(null).lastSyncAt()).isEmpty();
    }
}
