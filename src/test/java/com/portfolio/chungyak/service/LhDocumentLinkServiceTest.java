package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RevisionState;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LhDocumentLinkServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 8, 31);

    private final AnnouncementRepository repo = mock(AnnouncementRepository.class);
    private final AnnouncementDocumentRepository docs = mock(AnnouncementDocumentRepository.class);
    private final Set<Long> withDocument = new HashSet<>();
    private final LhDocumentLinkService service = new LhDocumentLinkService(repo, docs);

    @BeforeEach
    void setUp() {
        when(repo.findOldestLhNoticeDate()).thenReturn(Optional.of(LocalDate.of(2026, 8, 12)));
        when(docs.existsByAnnouncementId(anyLong())).thenAnswer(inv -> withDocument.contains(inv.<Long>getArgument(0)));
    }

    private static Announcement announcement(long id, String externalId, String name, LocalDate date,
                                             String lineage, RevisionState state) {
        Announcement a = Announcement.builder()
                .externalId(externalId).houseManageNo("x").pblancNo("x").houseName(name)
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PUBLIC)
                .noticeDate(date).lineageKey(lineage).revisionState(state).build();
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private void lh(List<Announcement> pool) {
        when(repo.findByExternalIdStartingWithAndNoticeDateBetween(eq("LH-"), any(), any())).thenReturn(pool);
    }

    @Test
    @DisplayName("LH 짝이 정정됐으면 계보 최신본(정정본)의 공고문을 가리킨다")
    void linksToCurrentRevision() {
        Announcement applyhome = announcement(43, "APPLYHOME-1-1", "인천계양지구 A6블록 공공분양주택(본청약)", D, null, RevisionState.CURRENT);
        Announcement original = announcement(3309, "LH-1", "인천계양 A6블록 공공분양주택 입주자모집공고", D, "k", RevisionState.SUPERSEDED);
        Announcement corrected = announcement(3172, "LH-2", "[정정공고]인천계양 A6블록 공공분양주택 입주자모집공고",
                D.plusDays(14), "k", RevisionState.CURRENT);
        when(repo.findLhSuppliedOnApplyhome(any())).thenReturn(List.of(applyhome));
        lh(List.of(original));
        when(repo.findByLineageKey("k")).thenReturn(List.of(original, corrected));
        withDocument.addAll(List.of(3309L, 3172L));

        LhDocumentLinkService.LinkReport report = service.linkAll();

        assertThat(applyhome.getDocumentAnnouncementId()).isEqualTo(3172L);
        assertThat(applyhome.documentOwnerId()).isEqualTo(3172L);
        assertThat(report.linked()).isEqualTo(1);
    }

    @Test
    @DisplayName("짝이 여러 계보면 잇지 않는다")
    void ambiguousNotLinked() {
        Announcement applyhome = announcement(1, "APPLYHOME-1-1", "인천계양지구 A6블록 공공분양주택", D, null, RevisionState.CURRENT);
        when(repo.findLhSuppliedOnApplyhome(any())).thenReturn(List.of(applyhome));
        lh(List.of(announcement(10, "LH-1", "인천계양 A6블록 공공분양주택", D, "k1", RevisionState.CURRENT),
                announcement(11, "LH-2", "인천계양 A6블록 공공분양주택 추가모집", D, "k2", RevisionState.CURRENT)));
        withDocument.addAll(List.of(10L, 11L));

        assertThat(service.linkAll().ambiguous()).isEqualTo(1);
        assertThat(applyhome.getDocumentAnnouncementId()).isNull();
    }

    @Test
    @DisplayName("자기 공고문이 생기면 연결을 끊는다, 짝이 취소됐으면 끊는다")
    void unlinksWhenOwnDocumentOrCancelled() {
        Announcement own = announcement(1, "APPLYHOME-1-1", "인천계양지구 A6블록 공공분양주택", D, null, RevisionState.CURRENT);
        own.linkDocumentTo(10L);
        withDocument.add(1L);
        Announcement cancelledTwin = announcement(2, "APPLYHOME-2-2", "시흥하중지구 A-4블록 신혼희망타운", D, null, RevisionState.CURRENT);
        cancelledTwin.linkDocumentTo(20L);
        when(repo.findLhSuppliedOnApplyhome(any())).thenReturn(List.of(own, cancelledTwin));
        Announcement lhCancelled = announcement(20, "LH-3", "시흥하중 A-4블록 신혼희망타운", D, "c", RevisionState.CANCELLED);
        lh(List.of(lhCancelled));
        when(repo.findByLineageKey("c")).thenReturn(List.of(lhCancelled));
        withDocument.add(20L);

        assertThat(service.linkAll().unlinked()).isEqualTo(2);
        assertThat(own.getDocumentAnnouncementId()).isNull();
        assertThat(cancelledTwin.getDocumentAnnouncementId()).isNull();
    }

    @Test
    @DisplayName("LH 짝에 공고문이 아직 없으면 잇지 않는다")
    void twinWithoutDocument() {
        Announcement applyhome = announcement(1, "APPLYHOME-1-1", "인천계양 A17블록 신혼희망타운(공공분양)(본청약)", D, null, RevisionState.CURRENT);
        when(repo.findLhSuppliedOnApplyhome(any())).thenReturn(List.of(applyhome));
        lh(List.of(announcement(10, "LH-1", "인천계양 A17블록 신혼희망타운(공공분양) 입주자모집공고", D, null, RevisionState.CURRENT)));

        assertThat(service.linkAll().noTwin()).isEqualTo(1);
        assertThat(applyhome.getDocumentAnnouncementId()).isNull();
    }
}
