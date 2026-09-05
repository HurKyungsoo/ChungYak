package com.portfolio.chungyak.service;

import com.portfolio.chungyak.alert.NewAnnouncementAlertService;
import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.external.AnnouncementSource;
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
 * 수집 소스는 {@link AnnouncementSource} 구현체 전부(청약홈·LH…)를 순회한다.
 * 각 소스가 게이트웨이·응답 구조 차이를 흡수해 ExternalAnnouncement 로 정규화한다.
 *
 * 수동 호출(/api/admin/sync)과 정기 배치가 겹치면 같은 행을 동시에 갱신해
 * 커넥션이 깨진다. 단일 인스턴스 전제로 JVM 플래그로 막는다.
 * (여러 인스턴스로 늘면 분산 락으로 교체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementSyncScheduler {

    private final List<AnnouncementSource> sources;
    private final AnnouncementSyncService syncService;
    private final AnnouncementRepository announcementRepository;
    private final PublicDataProperties properties;   // sync.minExpectedRecords 확인용
    private final SyncStatus syncStatus;
    private final Clock clock;
    private final NewAnnouncementAlertService alertService;

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
        SyncReport total = SyncReport.empty();
        List<Announcement> newAnnouncements = new ArrayList<>();
        for (AnnouncementSource source : sources) {
            if (!source.isEnabled()) {
                log.info("{} 소스 비활성 — 건너뜀", source.sourceName());
                continue;
            }
            SourceSyncResult result = syncSource(source);
            log.info("{} 동기화 완료. {}", source.sourceName(), result.report());
            total = total.plus(result.report());
            newAnnouncements.addAll(result.created());
        }
        log.info("전체 동기화 완료. {}", total);

        // 새 공고 알림 — 이번 배치에서 새로 생긴 공고만 대상으로 한다("새 공고" 알림의 정의 그대로).
        // 여기서 실패해도 수집 자체는 이미 끝났으니 배치를 실패시키지 않는다.
        try {
            alertService.notifyMatchingSubscribers(newAnnouncements);
        } catch (RuntimeException e) {
            log.warn("새 공고 알림 발송 중 오류 — 다음 배치에서 다시 시도되지 않으니 원인 확인 필요. {}", e.toString());
        }

        return total;
    }

    private record SourceSyncResult(SyncReport report, List<Announcement> created) {}

    private SourceSyncResult syncSource(AnnouncementSource source) {
        int pagesFetched = 0;
        int received = 0;
        int created = 0;
        int updated = 0;
        List<Announcement> createdAnnouncements = new ArrayList<>();

        for (int page = 1; page <= source.maxPages(); page++) {
            List<ExternalAnnouncement> announcements = source.fetchAnnouncements(page);
            if (announcements.isEmpty()) {
                log.info("{} {}페이지에서 결과 없음 — 수집 종료", source.sourceName(), page);
                break;
            }
            pagesFetched++;

            // 신규 공고에만 주택형 조회를 붙인다 (공고당 1회 추가 호출)
            List<ExternalAnnouncement> enriched = new ArrayList<>(announcements.size());
            for (ExternalAnnouncement announcement : announcements) {
                boolean isNew = announcementRepository
                        .findByExternalId(announcement.getExternalId()).isEmpty();

                if (isNew) {
                    List<ExternalUnitType> unitTypes = source.fetchUnitTypes(announcement);
                    String noticeContent = fetchNoticeContentSafely(source, announcement);
                    enriched.add(withUnitTypes(announcement, unitTypes, noticeContent));
                } else {
                    enriched.add(announcement);
                }
            }

            AnnouncementSyncService.SyncStat stat = syncService.sync(enriched);
            received += stat.received();
            created += stat.created();
            updated += stat.updated();
            createdAnnouncements.addAll(stat.createdAnnouncements());
        }

        return new SourceSyncResult(new SyncReport(pagesFetched, received, created, updated), createdAnnouncements);
    }

    /** 공고문 원문 조회 실패가 수집 전체를 막지 않도록 감싼다 (mock 이 null 을 줄 수도 있다). */
    private String fetchNoticeContentSafely(AnnouncementSource source, ExternalAnnouncement announcement) {
        try {
            var result = source.fetchNoticeContent(announcement);
            return result != null ? result.orElse(null) : null;
        } catch (RuntimeException e) {
            log.warn("{} 공고문 원문 조회 실패 — {}", source.sourceName(), e.toString());
            return null;
        }
    }

    private ExternalAnnouncement withUnitTypes(ExternalAnnouncement source,
                                               List<ExternalUnitType> unitTypes,
                                               String noticeContent) {
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
                .noticeContent(noticeContent != null ? noticeContent : source.getNoticeContent())
                .providerParams(source.getProviderParams())
                .unitTypes(unitTypes)
                .build();
    }

    public record SyncReport(int pagesFetched, int received, int created, int updated) {

        static SyncReport empty() {
            return new SyncReport(0, 0, 0, 0);
        }

        SyncReport plus(SyncReport o) {
            return new SyncReport(pagesFetched + o.pagesFetched, received + o.received,
                    created + o.created, updated + o.updated);
        }
    }

    public static class SyncAlreadyRunningException extends RuntimeException {
        public SyncAlreadyRunningException() {
            super("동기화가 이미 진행 중입니다.");
        }
    }
}
