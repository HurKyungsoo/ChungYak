package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.SupplyCategory;
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

    /**
     * 접수중 또는 접수예정 공고. region / detailType / keyword 가 null·공백이면 그 조건은 건너뛴다.
     * keyword 는 공고명 부분 일치(대소문자 무시)로 본다.
     */
    @Transactional(readOnly = true)
    public List<Announcement> findOpenOrUpcoming(String region, HouseDetailType detailType, String keyword) {
        return findOpenOrUpcoming(region, detailType, keyword, null);
    }

    /** 위 조건 + 공고 구분(분양/임대/신혼희망타운). category 가 null 이면 구분하지 않는다. */
    @Transactional(readOnly = true)
    public List<Announcement> findOpenOrUpcoming(String region, HouseDetailType detailType, String keyword,
                                                 SupplyCategory category) {
        LocalDate today = LocalDate.now(clock);
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase();
        return announcementRepository.findOpenWithUnitTypes(today).stream()
                .filter(Announcement::isCurrentRevision)   // 정정 전 본·취소된 공고는 목록에 두지 않는다 (#47)
                .filter(a -> region == null || region.isBlank()
                        || canonicalRegion(region).equals(canonicalRegion(a.getRegionName())))
                .filter(a -> detailType == null || detailType == a.getHouseDetailType())
                .filter(a -> category == null || category == a.getSupplyCategory())
                .filter(a -> normalizedKeyword == null || normalizedKeyword.isBlank()
                        || (a.getHouseName() != null && a.getHouseName().toLowerCase().contains(normalizedKeyword)))
                .sorted(Comparator.comparing(Announcement::getReceptBeginDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** 수집된 전체 공고 수 — 목록 화면 상단 통계 */
    @Transactional(readOnly = true)
    public long totalCount() {
        return announcementRepository.count();
    }

    /** 필터 드롭다운에 쓸 지역명 목록 */
    @Transactional(readOnly = true)
    public List<String> availableRegions() {
        LocalDate today = LocalDate.now(clock);
        return announcementRepository.findOpenWithUnitTypes(today).stream()
                .filter(Announcement::isCurrentRevision)
                .map(Announcement::getRegionName)
                .filter(r -> r != null && !r.isBlank())
                .map(this::canonicalRegion)
                .distinct()
                .sorted()
                .toList();
    }

    /** 원천 데이터의 시·도 표기를 이용자용 짧은 표기로 통일한다. */
    public String canonicalRegion(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        return switch (raw.trim()) {
            case "서울특별시" -> "서울";
            case "경기도" -> "경기";
            case "부산광역시" -> "부산";
            case "대구광역시" -> "대구";
            case "대전광역시" -> "대전";
            case "인천광역시" -> "인천";
            case "광주광역시" -> "광주";
            case "울산광역시" -> "울산";
            case "충청북도" -> "충북";
            case "충청남도" -> "충남";
            case "전라북도", "전북특별자치도" -> "전북";
            case "전라남도" -> "전남";
            case "경상북도" -> "경북";
            case "경상남도" -> "경남";
            case "강원도", "강원특별자치도" -> "강원";
            case "제주특별자치도" -> "제주";
            default -> raw.trim();
        };
    }

    @Transactional(readOnly = true)
    public Optional<Announcement> findDetail(Long id) {
        return announcementRepository.findByIdWithUnitTypes(id);
    }

    /**
     * 같은 계보의 다른 공고(정정 전 본·정정본·취소공고), 최근 공고일 순 (#47).
     * 계보 키가 없으면 빈 목록.
     */
    @Transactional(readOnly = true)
    public List<Announcement> findRevisions(Announcement announcement) {
        if (announcement.getLineageKey() == null) return List.of();
        return announcementRepository.findByLineageKey(announcement.getLineageKey()).stream()
                .filter(a -> !a.getId().equals(announcement.getId()))
                .sorted(Comparator.comparing(Announcement::getNoticeDate,
                                Comparator.nullsLast(Comparator.<LocalDate>naturalOrder())).reversed()
                        .thenComparing(Announcement::getId, Comparator.reverseOrder()))
                .toList();
    }

    /** 오늘 (Clock 기준) — 목록 뷰 모델의 D-day 계산에 넘긴다 */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 오늘 기준 공고 상태 — 화면 표시용 */
    public String statusOf(Announcement a) {
        LocalDate today = LocalDate.now(clock);
        if (a.isCancelled()) return "취소된 공고";
        if (a.isSuperseded()) return "정정 전 공고";
        if (a.isOpen(today)) return "접수중";
        if (a.getReceptBeginDate() != null && today.isBefore(a.getReceptBeginDate())) return "접수예정";
        if (a.isClosed(today)) return "접수마감";
        return "-";
    }
}
