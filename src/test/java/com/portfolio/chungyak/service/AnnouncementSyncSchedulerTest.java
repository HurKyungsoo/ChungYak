package com.portfolio.chungyak.service;

import com.portfolio.chungyak.alert.NewAnnouncementAlertService;
import com.portfolio.chungyak.external.ApplyhomeClient;
import com.portfolio.chungyak.external.PublicDataProperties;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.service.AnnouncementSyncScheduler.SyncReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 수집 스케줄러가 실패·이상을 SyncStatus 에 기록하는지.
 */
class AnnouncementSyncSchedulerTest {

    private ApplyhomeClient applyhomeClient;
    private AnnouncementSyncService syncService;
    private SyncStatus syncStatus;
    private AnnouncementSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        applyhomeClient = Mockito.mock(ApplyhomeClient.class);
        when(applyhomeClient.isEnabled()).thenReturn(true);
        when(applyhomeClient.sourceName()).thenReturn("청약홈");
        when(applyhomeClient.maxPages()).thenReturn(3);
        syncService = Mockito.mock(AnnouncementSyncService.class);
        AnnouncementRepository repo = Mockito.mock(AnnouncementRepository.class);
        PublicDataProperties props = new PublicDataProperties();
        props.getApplyhome().setMaxPages(3);
        props.getSync().setMinExpectedRecords(1000);
        syncStatus = new SyncStatus();
        NewAnnouncementAlertService alertService = Mockito.mock(NewAnnouncementAlertService.class);
        scheduler = new AnnouncementSyncScheduler(List.of(applyhomeClient), syncService, repo, props,
                syncStatus, Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"), ZoneOffset.UTC), alertService);
    }

    @Test
    @DisplayName("첫 페이지부터 비면 (키 만료 등) → 0건 수집, SyncStatus = LOW_YIELD")
    void emptyFirstPageIsLowYield() {
        when(applyhomeClient.fetchAnnouncements(anyInt())).thenReturn(List.of());

        SyncReport report = scheduler.runSync();

        assertThat(report.received()).isZero();
        assertThat(syncStatus.getOutcome()).isEqualTo(SyncStatus.Outcome.LOW_YIELD);
        assertThat(syncStatus.getLastSuccessAt()).isNull();
    }

    @Test
    @DisplayName("수집 중 예외 → SyncStatus = FAILED, 예외 재전파")
    void exceptionIsRecordedAndRethrown() {
        when(applyhomeClient.fetchAnnouncements(anyInt()))
                .thenThrow(new IllegalStateException("DB down"));

        assertThatThrownBy(() -> scheduler.runSync())
                .isInstanceOf(IllegalStateException.class);

        assertThat(syncStatus.getOutcome()).isEqualTo(SyncStatus.Outcome.FAILED);
        assertThat(syncStatus.getDetail()).contains("DB down");
    }

    @Test
    @DisplayName("정기 배치는 예외가 나도 스레드를 죽이지 않는다")
    void dailyBatchSwallowsException() {
        when(applyhomeClient.fetchAnnouncements(anyInt()))
                .thenThrow(new IllegalStateException("boom"));

        scheduler.syncDaily();   // 예외를 던지지 않아야 한다

        assertThat(syncStatus.getOutcome()).isEqualTo(SyncStatus.Outcome.FAILED);
    }
}
