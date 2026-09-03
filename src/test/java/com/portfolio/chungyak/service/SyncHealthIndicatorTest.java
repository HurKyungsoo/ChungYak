package com.portfolio.chungyak.service;

import com.portfolio.chungyak.external.PublicDataProperties;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 상태 -> /actuator/health 매핑 검증.
 */
class SyncHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-09-03T20:00:00Z");

    private static PublicDataProperties props() {
        PublicDataProperties p = new PublicDataProperties();
        p.getSync().setMinExpectedRecords(1000);
        p.getSync().setStaleAfterHours(30);
        return p;
    }

    private static SyncHealthIndicator indicator(SyncStatus status, Instant now) {
        return new SyncHealthIndicator(status, Clock.fixed(now, ZoneOffset.UTC), props());
    }

    @Test
    @DisplayName("아직 안 돌림 → UNKNOWN")
    void neverRun() {
        assertThat(indicator(new SyncStatus(), NOW).health().getStatus())
                .isEqualTo(Status.UNKNOWN);
    }

    @Test
    @DisplayName("예외로 실패 → DOWN")
    void failed() {
        SyncStatus s = new SyncStatus();
        s.recordAttempt(NOW);
        s.recordFailure(new RuntimeException("401 Unauthorized"));

        assertThat(indicator(s, NOW).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("수집 건수가 기대 미만 → DOWN (LOW_YIELD)")
    void lowYield() {
        SyncStatus s = new SyncStatus();
        s.recordAttempt(NOW);
        s.recordSuccess(new SyncReport(3, 12, 12, 0), 1000, NOW);

        assertThat(s.getOutcome()).isEqualTo(SyncStatus.Outcome.LOW_YIELD);
        assertThat(indicator(s, NOW).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("정상 수집, 방금 → UP")
    void freshSuccess() {
        SyncStatus s = new SyncStatus();
        s.recordAttempt(NOW);
        s.recordSuccess(new SyncReport(29, 2861, 2861, 0), 1000, NOW);

        assertThat(indicator(s, NOW).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("정상 수집이지만 30시간 넘게 지남 → DOWN (stale)")
    void staleSuccess() {
        SyncStatus s = new SyncStatus();
        s.recordAttempt(NOW);
        s.recordSuccess(new SyncReport(29, 2861, 2861, 0), 1000, NOW);

        Instant muchLater = NOW.plusSeconds(31 * 3600);
        assertThat(indicator(s, muchLater).health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator(s, NOW.plusSeconds(20 * 3600)).health().getStatus()).isEqualTo(Status.UP);
    }
}
