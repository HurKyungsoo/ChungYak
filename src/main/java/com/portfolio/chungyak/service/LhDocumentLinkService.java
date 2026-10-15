package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.LhNoticeTwin;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 청약홈에 올라온 LH 공급 공고에 LH 쪽 같은 공고의 공고문을 잇는다.
 *
 * 청약홈 페이지에는 PDF 가 없어(LH 청약플러스 링크만) 이 공고들은 공고문 Q&A·요약이 꺼져 있었다 — 그런데 신혼희망타운·
 * 공공분양은 특별공급 판정 대상이라 판정 뒤 "소득 기준이 정확히 얼마냐"를 공고문에서 확인하는 흐름이 가장 필요한 공고다.
 * 원문을 복사하지 않고 {@code document_announcement_id} 로 가리킨다. 짝은 {@link LhNoticeTwin}(공고일 + 지구명·블록)로만
 * 찾고, 여럿이거나 없으면 잇지 않는다. LH 짝이 정정됐으면 계보 최신본을 가리킨다(#47).
 * LH 수집은 2026-08-12 공고부터라 그 이전 청약홈 공고에는 짝이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LhDocumentLinkService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementDocumentRepository documentRepository;

    public record LinkReport(int candidates, int linked, int unlinked, int noTwin, int ambiguous) {}

    @Transactional
    public LinkReport linkAll() {
        Optional<java.time.LocalDate> oldestLh = announcementRepository.findOldestLhNoticeDate();
        if (oldestLh.isEmpty()) return new LinkReport(0, 0, 0, 0, 0);
        List<Announcement> candidates = announcementRepository.findLhSuppliedOnApplyhome(
                oldestLh.get().minusDays(LhNoticeTwin.NOTICE_DATE_TOLERANCE_DAYS));

        int linked = 0, unlinked = 0, noTwin = 0, ambiguous = 0;
        for (Announcement a : candidates) {
            // 자기 공고문이 생기면(청약홈이 PDF 를 올리기 시작하면) 그것을 쓴다
            if (documentRepository.existsByAnnouncementId(a.getId())) {
                if (a.borrowsDocument()) {
                    a.linkDocumentTo(null);
                    unlinked++;
                }
                continue;
            }
            Optional<Long> target;
            try {
                target = twinDocumentOwner(a);
            } catch (AmbiguousTwinException e) {
                ambiguous++;
                log.warn("LH 짝 공고가 여럿 — 잇지 않음: 청약홈 #{} {} → {}", a.getId(), a.getHouseName(), e.getMessage());
                continue;
            }
            if (target.isEmpty()) {
                noTwin++;
                // 짝이 취소됐으면 예전 연결도 끊는다
                if (a.borrowsDocument()) {
                    a.linkDocumentTo(null);
                    unlinked++;
                }
                continue;
            }
            if (!target.get().equals(a.getDocumentAnnouncementId())) {
                a.linkDocumentTo(target.get());
                linked++;
                log.info("LH 공고문 연결 — 청약홈 #{} {} → LH #{}", a.getId(), a.getHouseName(), target.get());
            }
        }
        LinkReport report = new LinkReport(candidates.size(), linked, unlinked, noTwin, ambiguous);
        log.info("LH 공고문 연결 완료 — {}", report);
        return report;
    }

    /** 짝의 계보 최신본 중 공고문이 있는 것. 짝이 없거나 최신본에 공고문이 아직 없으면 empty */
    private Optional<Long> twinDocumentOwner(Announcement a) {
        if (a.getNoticeDate() == null) return Optional.empty();
        int tol = LhNoticeTwin.NOTICE_DATE_TOLERANCE_DAYS;
        List<Announcement> twins = announcementRepository.findByExternalIdStartingWithAndNoticeDateBetween(
                        "LH-", a.getNoticeDate().minusDays(tol), a.getNoticeDate().plusDays(tol)).stream()
                .filter(l -> LhNoticeTwin.sameNotice(a.getHouseName(), a.getNoticeDate(), l.getHouseName(), l.getNoticeDate()))
                .toList();
        if (twins.isEmpty()) return Optional.empty();

        // 원본과 정정본이 함께 걸린다 — 계보가 하나면 같은 공고다
        Set<String> lineages = twins.stream()
                .map(l -> l.getLineageKey() != null ? l.getLineageKey() : "id:" + l.getId())
                .collect(Collectors.toSet());
        if (lineages.size() > 1) {
            throw new AmbiguousTwinException(twins.stream().map(l -> "#" + l.getId()).collect(Collectors.joining(", ")));
        }
        Announcement any = twins.get(0);
        List<Announcement> members = any.getLineageKey() != null
                ? announcementRepository.findByLineageKey(any.getLineageKey()) : List.of(any);
        // 취소된 공고는 잇지 않는다 — 취소 공고문을 이 공고의 근거로 보여 주면 안 된다
        return members.stream()
                .filter(Announcement::isCurrentRevision)
                .map(Announcement::getId)
                .filter(documentRepository::existsByAnnouncementId)
                .findFirst();
    }

    private static final class AmbiguousTwinException extends RuntimeException {
        AmbiguousTwinException(String message) {
            super(message);
        }
    }
}
