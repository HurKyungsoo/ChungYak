package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 공고 조회 (읽기 전용).
 *
 * 저장소는 이미 있는 findOpenWithUnitTypes / findByIdWithUnitTypes 만 쓴다.
 * 지역·주택유형 필터는 데이터가 3천 건 규모라 메모리에서 거른다 —
 * 동적 쿼리를 붙이는 건 MyBatis 조회를 도입할 때 함께 한다.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementQueryService {

    private final AnnouncementRepository announcementRepository;
    private final Clock clock;

    /** 접수중 또는 접수예정 공고. region / detailType 이 null 이면 그 조건은 건너뛴다. */
    @Transactional(readOnly = true)
    public List<Announcement> findOpenOrUpcoming(String region, HouseDetailType detailType) {
        LocalDate today = LocalDate.now(clock);
        return announcementRepository.findOpenWithUnitTypes(today).stream()
                .filter(a -> region == null || region.isBlank()
                        || region.equals(a.getRegionName()))
                .filter(a -> detailType == null || detailType == a.getHouseDetailType())
                .sorted(Comparator.comparing(Announcement::getReceptBeginDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 필터 드롭다운에 쓸 지역명 목록 */
    @Transactional(readOnly = true)
    public List<String> availableRegions() {
        LocalDate today = LocalDate.now(clock);
        return announcementRepository.findOpenWithUnitTypes(today).stream()
                .map(Announcement::getRegionName)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Announcement> findDetail(Long id) {
        return announcementRepository.findByIdWithUnitTypes(id);
    }

    /** 오늘 기준 공고 상태 — 화면 표시용 */
    public String statusOf(Announcement a) {
        LocalDate today = LocalDate.now(clock);
        if (a.isOpen(today)) return "접수중";
        if (a.getReceptBeginDate() != null && today.isBefore(a.getReceptBeginDate())) return "접수예정";
        if (a.isClosed(today)) return "접수마감";
        return "-";
    }
}
