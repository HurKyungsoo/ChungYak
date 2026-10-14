package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.NoticeKind;
import com.portfolio.chungyak.domain.RevisionState;
import com.portfolio.chungyak.external.ExternalAnnouncement;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 정정·취소공고 수집 (#47) — 계보 상태 재계산과 "새 공고" 알림 대상. */
class AnnouncementSyncServiceTest {

    private final AnnouncementRepository repository = Mockito.mock(AnnouncementRepository.class);
    private final AnnouncementSyncService service = new AnnouncementSyncService(
            repository, Mockito.mock(AnnouncementDocumentRepository.class), Clock.systemUTC());

    private ExternalAnnouncement external(String id, String title, NoticeKind kind, LocalDate noticeDate) {
        return ExternalAnnouncement.builder()
                .externalId("LH-" + id).houseManageNo("03").pblancNo(id).houseName(title)
                .houseType(HouseType.UNKNOWN).houseDetailType(HouseDetailType.PUBLIC)
                .noticeDate(noticeDate).noticeKind(kind).lineageKey("lineage")
                .build();
    }

    @Test
    @DisplayName("이미 아는 공고의 정정본은 저장하되 새 공고 알림 대상이 아니고, 원래 공고는 정정 전으로 내려간다")
    void correctionOfKnownNotice() {
        Announcement original = Announcement.builder()
                .externalId("LH-855").houseManageNo("03").pblancNo("855").houseName("대구연호 통합공공임대")
                .houseType(HouseType.UNKNOWN).houseDetailType(HouseDetailType.PUBLIC)
                .noticeDate(LocalDate.of(2026, 9, 30)).lineageKey("lineage").build();
        List<Announcement> stored = new ArrayList<>(List.of(original));
        when(repository.findByExternalId(any())).thenReturn(Optional.empty());
        when(repository.existsByLineageKey("lineage")).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> {
            stored.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repository.findByLineageKey("lineage")).thenReturn(stored);

        var stat = service.sync(List.of(external("878", "[정정공고]대구연호 통합공공임대",
                NoticeKind.CORRECTION, LocalDate.of(2026, 10, 6))));

        assertThat(stat.created()).isEqualTo(1);
        assertThat(stat.createdAnnouncements()).isEmpty();
        assertThat(original.getRevisionState()).isEqualTo(RevisionState.SUPERSEDED);
        assertThat(stored.get(1).getRevisionState()).isEqualTo(RevisionState.CURRENT);
        assertThat(stored.get(1).getNoticeKind()).isEqualTo(NoticeKind.CORRECTION);
    }

    @Test
    @DisplayName("계보가 처음 보이는 공고는 정정본이어도 새 공고 알림 대상이다")
    void firstSeenCorrectionIsNew() {
        List<Announcement> stored = new ArrayList<>();
        when(repository.findByExternalId(any())).thenReturn(Optional.empty());
        when(repository.existsByLineageKey("lineage")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            stored.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(repository.findByLineageKey("lineage")).thenReturn(stored);

        var stat = service.sync(List.of(external("878", "[정정공고]대구연호 통합공공임대",
                NoticeKind.CORRECTION, LocalDate.of(2026, 10, 6))));

        assertThat(stat.createdAnnouncements()).hasSize(1);
    }
}
