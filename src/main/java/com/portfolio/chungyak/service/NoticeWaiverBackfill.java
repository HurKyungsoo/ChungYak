package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.AnnouncementDocument;
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
 * 이미 저장된 공고문으로 "소득·자산 … 불문" 표시(announcement.requirements_waived, V15)를 채운다 — 기동 때 한 번.
 *
 * 새 공고문은 수집 때({@link AnnouncementSyncService}) 바로 판별하지만, V15 전에 받아 둔 원문은 다시 받지 않는 한 표시가 비어 있다.
 * 아직 확인 안 한(NULL) 공고만 본다 — 두 번째 기동부터는 공고문 수(수백 건)만큼 조회하고 끝난다. 실패해도 앱은 뜬다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeWaiverBackfill {

    private final AnnouncementDocumentRepository documentRepository;
    private final AnnouncementRepository announcementRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        try {
            int checked = 0, waived = 0;
            for (AnnouncementDocument doc : documentRepository.findAll()) {
                var announcement = announcementRepository.findById(doc.getAnnouncementId()).orElse(null);
                if (announcement == null || announcement.getRequirementsWaived() != null) continue;
                boolean w = NoticeWaivers.waivesIncomeAndAssets(doc.getRawText());
                announcement.markRequirementsWaived(w);
                checked++;
                if (w) waived++;
            }
            if (checked > 0) log.info("공고문 '소득·자산 불문' 표시 백필 — 확인 {}건, 불문 {}건", checked, waived);
        } catch (RuntimeException e) {
            log.warn("공고문 '소득·자산 불문' 백필 실패 — 다음 기동·수집 때 다시: {}", e.toString());
        }
    }
}
