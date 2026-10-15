package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByExternalId(String externalId);

    /** 같은 공고 계보(원래 공고 + 정정본 + 취소공고) — #47 */
    List<Announcement> findByLineageKey(String lineageKey);

    boolean existsByLineageKey(String lineageKey);

    /** 청약홈에 올라온 LH 공급 공고(신혼희망타운·LH 시행 공공분양) — LH 공고문 연결 대상. 무순위는 뺀다 */
    @Query("select a from Announcement a where a.externalId like 'APPLYHOME-%' and a.externalId not like 'APPLYHOME-RM-%' "
            + "and (a.developerName like '%토지주택%' or a.houseType = com.portfolio.chungyak.domain.HouseType.NEWLYWED_HOPE_TOWN) "
            + "and a.noticeDate >= :since")
    List<Announcement> findLhSuppliedOnApplyhome(@Param("since") LocalDate since);

    @Query("select min(a.noticeDate) from Announcement a where a.externalId like 'LH-%'")
    Optional<LocalDate> findOldestLhNoticeDate();

    List<Announcement> findByExternalIdStartingWithAndNoticeDateBetween(String prefix, LocalDate from, LocalDate to);

    /** 접수 진행 중 또는 예정인 공고 */
    Page<Announcement> findByReceptEndDateGreaterThanEqualOrderByReceptBeginDateAsc(
            LocalDate today, Pageable pageable);

    /** 판정용 조회 — 주택형까지 한 번에 가져온다 (N+1 방지) */
    @Query("select distinct a from Announcement a left join fetch a.unitTypes "
            + "where a.receptEndDate >= :today")
    List<Announcement> findOpenWithUnitTypes(@Param("today") LocalDate today);

    @Query("select distinct a from Announcement a left join fetch a.unitTypes where a.id = :id")
    Optional<Announcement> findByIdWithUnitTypes(@Param("id") Long id);

    /** 마감 임박 알림용 — 접수마감일이 정확히 이 날짜인 공고. */
    @Query("select distinct a from Announcement a left join fetch a.unitTypes "
            + "where a.receptEndDate = :date")
    List<Announcement> findByReceptEndDateWithUnitTypes(@Param("date") LocalDate date);
}
