package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.AnnouncementDocument;
import com.portfolio.chungyak.domain.NoticeBaseDate;
import com.portfolio.chungyak.domain.NoticeWaivers;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미 저장된 공고문으로 공고의 두 표시를 채운다 — 기동 때 한 번.
 * <ul>
 *   <li>"소득·자산 … 불문"(requirements_waived, V15) — {@link NoticeWaivers}</li>
 *   <li>입주자모집공고일 = 자격 판단 기준일(notice_base_date, V16) — {@link NoticeBaseDate}</li>
 * </ul>
 * 새 공고문은 수집 때({@link AnnouncementSyncService}) 바로 채우지만, 그 전에 받아 둔 원문은 다시 받지 않는 한 비어 있다.
 * 비어 있는 것만 본다. 기준일은 공고문 앞쪽에 있어 첫 조각만 읽는다. 실패해도 앱은 뜬다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeTextBackfill {

    private final AnnouncementDocumentRepository documentRepository;
    private final AnnouncementRepository announcementRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        try {
            int waivedChecked = 0, waived = 0, baseDates = 0;
            for (AnnouncementDocument doc : documentRepository.findAll()) {
                var announcement = announcementRepository.findById(doc.getAnnouncementId()).orElse(null);
                if (announcement == null) continue;
                if (announcement.getRequirementsWaived() == null) {
                    boolean w = NoticeWaivers.waivesIncomeAndAssets(doc.getRawText());
                    announcement.markRequirementsWaived(w);
                    waivedChecked++;
                    if (w) waived++;
                }
                if (announcement.getNoticeBaseDate() == null) {
                    announcement.markNoticeBaseDate(NoticeBaseDate.find(doc.getFirstPart()));
                    if (announcement.getNoticeBaseDate() != null) baseDates++;
                }
            }
            if (waivedChecked > 0 || baseDates > 0) {
                log.info("공고문 표시 백필 — '소득·자산 불문' 확인 {}건(불문 {}건), 입주자모집공고일 {}건", waivedChecked, waived, baseDates);
            }
        } catch (RuntimeException e) {
            log.warn("공고문 표시 백필 실패 — 다음 기동·수집 때 다시: {}", e.toString());
        }
    }
}
