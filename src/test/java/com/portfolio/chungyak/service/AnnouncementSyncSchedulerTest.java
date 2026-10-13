package com.portfolio.chungyak.service;

import com.portfolio.chungyak.alert.NewAnnouncementAlertService;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.external.ApplyhomeClient;
import com.portfolio.chungyak.external.ExternalAnnouncement;
import com.portfolio.chungyak.external.LhClient;
import com.portfolio.chungyak.external.PublicDataProperties;
import com.portfolio.chungyak.rag.AnnouncementIndexer;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
                syncStatus, Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"), ZoneOffset.UTC), alertService,
                Mockito.mock(AnnouncementIndexer.class), Mockito.mock(AnnouncementDocumentRepository.class));
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

    @Test
    @DisplayName("기존 공고: 상세를 못 채운 것만 상세 재조회(백필), 주택형은 다시 받지 않는다 / 신규: 상세+주택형")
    void detailBackfillOnlyForExistingWithoutDetail() {
        LhClient lh = Mockito.mock(LhClient.class);
        when(lh.isEnabled()).thenReturn(true);
        when(lh.sourceName()).thenReturn("LH");
        when(lh.maxPages()).thenReturn(1);
        ExternalAnnouncement needsBackfill = listed("LH-1");
        ExternalAnnouncement alreadyDetailed = listed("LH-2");
        ExternalAnnouncement brandNew = listed("LH-3");
        when(lh.fetchAnnouncements(1)).thenReturn(List.of(needsBackfill, alreadyDetailed, brandNew));
        when(lh.enrichWithDetail(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lh.fetchUnitTypes(any())).thenReturn(List.of());

        Announcement withoutPdf = Announcement.builder().externalId("LH-1").build();
        Announcement withPdf = Announcement.builder().externalId("LH-2").noticePdfUrl("u").build();
        AnnouncementRepository repo = Mockito.mock(AnnouncementRepository.class);
        when(repo.findByExternalId("LH-1")).thenReturn(Optional.of(withoutPdf));
        when(repo.findByExternalId("LH-2")).thenReturn(Optional.of(withPdf));
        when(repo.findByExternalId("LH-3")).thenReturn(Optional.empty());
        when(lh.needsDetailBackfill(withoutPdf)).thenReturn(true);
        when(lh.needsDetailBackfill(withPdf)).thenReturn(false);
        when(syncService.sync(any())).thenReturn(new AnnouncementSyncService.SyncStat(3, 1, 2, List.of()));

        PublicDataProperties props = new PublicDataProperties();
        new AnnouncementSyncScheduler(List.of(lh), syncService, repo, props, syncStatus,
                Clock.fixed(Instant.parse("2026-10-12T04:00:00Z"), ZoneOffset.UTC),
                Mockito.mock(NewAnnouncementAlertService.class), Mockito.mock(AnnouncementIndexer.class),
                Mockito.mock(AnnouncementDocumentRepository.class)).runSync();

        verify(lh).enrichWithDetail(needsBackfill);
        verify(lh, never()).enrichWithDetail(alreadyDetailed);
        verify(lh).enrichWithDetail(brandNew);
        verify(lh, never()).fetchUnitTypes(needsBackfill);
        verify(lh).fetchUnitTypes(brandNew);
    }

    @Test
    @DisplayName("기존 공고: 예전 수집에서 15,500자로 잘린 원문만 다시 받아 넘긴다 — 전문인 원문은 다시 받지 않는다 (#29)")
    void refetchesLegacyTruncatedNoticeOnly() {
        LhClient lh = Mockito.mock(LhClient.class);
        when(lh.isEnabled()).thenReturn(true);
        when(lh.sourceName()).thenReturn("LH");
        when(lh.maxPages()).thenReturn(1);
        ExternalAnnouncement truncated = listed("LH-10");
        ExternalAnnouncement full = listed("LH-11");
        when(lh.fetchAnnouncements(1)).thenReturn(List.of(truncated, full));
        when(lh.fetchNoticeContent(truncated)).thenReturn(Optional.of("전문 공고문"));

        Announcement a10 = Announcement.builder().externalId("LH-10").noticePdfUrl("u").build();
        Announcement a11 = Announcement.builder().externalId("LH-11").noticePdfUrl("u").build();
        org.springframework.test.util.ReflectionTestUtils.setField(a10, "id", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(a11, "id", 11L);
        AnnouncementRepository repo = Mockito.mock(AnnouncementRepository.class);
        when(repo.findByExternalId("LH-10")).thenReturn(Optional.of(a10));
        when(repo.findByExternalId("LH-11")).thenReturn(Optional.of(a11));

        AnnouncementDocument legacy = new AnnouncementDocument(10L, "LH", "옛", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(legacy, "firstPart", "가".repeat(AnnouncementDocument.LEGACY_CAP));
        AnnouncementDocumentRepository docs = Mockito.mock(AnnouncementDocumentRepository.class);
        when(docs.findByAnnouncementId(10L)).thenReturn(Optional.of(legacy));
        when(docs.findByAnnouncementId(11L)).thenReturn(Optional.of(new AnnouncementDocument(11L, "LH", "전문", Instant.now())));
        when(syncService.sync(any())).thenReturn(new AnnouncementSyncService.SyncStat(2, 0, 2, List.of()));

        new AnnouncementSyncScheduler(List.of(lh), syncService, repo, new PublicDataProperties(), syncStatus,
                Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC),
                Mockito.mock(NewAnnouncementAlertService.class), Mockito.mock(AnnouncementIndexer.class), docs).runSync();

        verify(lh).fetchNoticeContent(truncated);
        verify(lh, never()).fetchNoticeContent(full);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<ExternalAnnouncement>> synced = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(syncService).sync(synced.capture());
        assertThat(synced.getValue()).extracting(ExternalAnnouncement::getNoticeContent).containsExactly("전문 공고문", null);
    }

    @Test
    @DisplayName("원문이 없는 기존 공고는 접수 전·중일 때만 한 번 받는다 — 마감 공고·원문 있는 공고는 받지 않는다 (#38)")
    void backfillsMissingNoticeOnlyForCurrent() {
        ApplyhomeClient applyhome = Mockito.mock(ApplyhomeClient.class);
        when(applyhome.isEnabled()).thenReturn(true);
        when(applyhome.sourceName()).thenReturn("청약홈");
        when(applyhome.maxPages()).thenReturn(1);
        when(applyhome.backfillsMissingNotice()).thenReturn(true);
        ExternalAnnouncement current = listed("A-20");
        ExternalAnnouncement closed = listed("A-21");
        ExternalAnnouncement hasDoc = listed("A-22");
        when(applyhome.fetchAnnouncements(1)).thenReturn(List.of(current, closed, hasDoc));
        when(applyhome.fetchNoticeContent(current)).thenReturn(Optional.of("공고문 전문"));

        java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 13);
        Announcement a20 = Announcement.builder().externalId("A-20").receptEndDate(today.plusDays(5)).build();
        Announcement a21 = Announcement.builder().externalId("A-21").receptEndDate(today.minusDays(1)).build();
        Announcement a22 = Announcement.builder().externalId("A-22").receptEndDate(today.plusDays(5)).build();
        org.springframework.test.util.ReflectionTestUtils.setField(a20, "id", 20L);
        org.springframework.test.util.ReflectionTestUtils.setField(a21, "id", 21L);
        org.springframework.test.util.ReflectionTestUtils.setField(a22, "id", 22L);
        AnnouncementRepository repo = Mockito.mock(AnnouncementRepository.class);
        when(repo.findByExternalId("A-20")).thenReturn(Optional.of(a20));
        when(repo.findByExternalId("A-21")).thenReturn(Optional.of(a21));
        when(repo.findByExternalId("A-22")).thenReturn(Optional.of(a22));
        AnnouncementDocumentRepository docs = Mockito.mock(AnnouncementDocumentRepository.class);
        when(docs.findByAnnouncementId(22L)).thenReturn(Optional.of(new AnnouncementDocument(22L, "APPLYHOME", "전문", Instant.now())));
        when(syncService.sync(any())).thenReturn(new AnnouncementSyncService.SyncStat(3, 0, 3, List.of()));

        new AnnouncementSyncScheduler(List.of(applyhome), syncService, repo, new PublicDataProperties(), syncStatus,
                Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC),
                Mockito.mock(NewAnnouncementAlertService.class), Mockito.mock(AnnouncementIndexer.class), docs).runSync();

        verify(applyhome).fetchNoticeContent(current);
        verify(applyhome, never()).fetchNoticeContent(closed);
        verify(applyhome, never()).fetchNoticeContent(hasDoc);
    }

    private static ExternalAnnouncement listed(String externalId) {
        return ExternalAnnouncement.builder().externalId(externalId).houseName("x").pblancNo(externalId).build();
    }
}
