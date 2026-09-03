package com.portfolio.chungyak.service;

import com.portfolio.chungyak.external.ApplyhomeClient;
import com.portfolio.chungyak.external.ExternalAnnouncement;
import com.portfolio.chungyak.external.ExternalUnitType;
import com.portfolio.chungyak.external.PublicDataProperties;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 매일 새벽 4시 공고 수집.
 *
 * 수동 호출(/api/admin/sync)과 정기 배치가 겹치면 같은 행을 동시에 갱신해
 * 커넥션이 깨진다. 단일 인스턴스 전제로 JVM 플래그로 막는다.
 * (여러 인스턴스로 늘면 분산 락으로 교체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementSyncScheduler {

    private final ApplyhomeClient applyhomeClient;
    private final AnnouncementSyncService syncService;
    private final AnnouncementRepository announcementRepository;
    private final PublicDataProperties properties;
    private final SyncStatus syncStatus;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncDaily() {
        try {
            runSync();
        } catch (SyncAlreadyRunningException e) {
            log.info("수동 동기화가 진행 중이라 이번 정기 배치는 건너뜁니다.");
        } catch (RuntimeException e) {
            // 이미 runSync 안에서 상태 기록·ERROR 로그를 남겼다. 배치 스레드는 계속 살려둔다.
            log.error("정기 수집 배치가 실패했습니다 — 다음 배치까지 데이터가 낡습니다.");
        }
    }

    public SyncReport runSync() {
        if (!running.compareAndSet(false, true)) {
            throw new SyncAlreadyRunningException();
        }
        syncStatus.recordAttempt(clock.instant());
        try {
            SyncReport report = doSync();
            int minExpected = properties.getSync().getMinExpectedRecords();
            syncStatus.recordSuccess(report, minExpected, clock.instant());
            if (report.received() < minExpected) {
                log.error("공고 수집 이상 — {}건만 수집됨 (기대 최소 {}건). API 키·응답을 확인하세요. {}",
                        report.received(), minExpected, report);
            }
            return report;
        } catch (RuntimeException e) {
            syncStatus.recordFailure(e);
            log.error("공고 수집 실패 — {}", e.toString(), e);
            throw e;
        } finally {
            running.set(false);
        }
    }

    private SyncReport doSync() {
        int maxPages = properties.getApplyhome().getMaxPages();
        int totalReceived = 0;
        int totalCreated = 0;
        int totalUpdated = 0;
        int pagesFetched = 0;

        for (int page = 1; page <= maxPages; page++) {
            List<ExternalAnnouncement> announcements = applyhomeClient.fetchAnnouncements(page);
            if (announcements.isEmpty()) {
                log.info("청약홈 {}페이지에서 결과 없음 — 수집 종료", page);
                break;
            }
            pagesFetched++;

            // 신규 공고에만 주택형 조회를 붙인다 (공고당 1회 추가 호출)
            List<ExternalAnnouncement> enriched = new ArrayList<>(announcements.size());
            for (ExternalAnnouncement announcement : announcements) {
                boolean isNew = announcementRepository
                        .findByExternalId(announcement.getExternalId()).isEmpty();

                if (isNew) {
                    List<ExternalUnitType> unitTypes = applyhomeClient.fetchUnitTypes(
                            announcement.getHouseManageNo(), announcement.getPblancNo());
                    enriched.add(withUnitTypes(announcement, unitTypes));
                } else {
                    enriched.add(announcement);
                }
            }

            AnnouncementSyncService.SyncStat stat = syncService.sync(enriched);
            totalReceived += stat.received();
            totalCreated += stat.created();
            totalUpdated += stat.updated();
        }

        SyncReport report = new SyncReport(pagesFetched, totalReceived, totalCreated, totalUpdated);
        log.info("청약홈 동기화 완료. {}", report);
        return report;
    }

    private ExternalAnnouncement withUnitTypes(ExternalAnnouncement source,
                                               List<ExternalUnitType> unitTypes) {
        return ExternalAnnouncement.builder()
                .externalId(source.getExternalId())
                .houseManageNo(source.getHouseManageNo())
                .pblancNo(source.getPblancNo())
                .houseName(source.getHouseName())
                .houseType(source.getHouseType())
                .houseDetailType(source.getHouseDetailType())
                .regionName(source.getRegionName())
                .regionCode(source.getRegionCode())
                .address(source.getAddress())
                .zipCode(source.getZipCode())
                .totalSupplyCount(source.getTotalSupplyCount())
                .noticeDate(source.getNoticeDate())
                .receptBeginDate(source.getReceptBeginDate())
                .receptEndDate(source.getReceptEndDate())
                .specialReceptBeginDate(source.getSpecialReceptBeginDate())
                .specialReceptEndDate(source.getSpecialReceptEndDate())
                .winnerAnnounceDate(source.getWinnerAnnounceDate())
                .regulationFlags(source.getRegulationFlags())
                .noticeUrl(source.getNoticeUrl())
                .homepageUrl(source.getHomepageUrl())
                .inquiryTel(source.getInquiryTel())
                .developerName(source.getDeveloperName())
                .constructorName(source.getConstructorName())
                .moveInYearMonth(source.getMoveInYearMonth())
                .noticeContent(source.getNoticeContent())
                .unitTypes(unitTypes)
                .build();
    }

    public record SyncReport(int pagesFetched, int received, int created, int updated) {}

    public static class SyncAlreadyRunningException extends RuntimeException {
        public SyncAlreadyRunningException() {
            super("동기화가 이미 진행 중입니다.");
        }
    }
}
