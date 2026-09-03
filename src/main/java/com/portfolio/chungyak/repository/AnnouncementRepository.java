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

    /** 접수 진행 중 또는 예정인 공고 */
    Page<Announcement> findByReceptEndDateGreaterThanEqualOrderByReceptBeginDateAsc(
            LocalDate today, Pageable pageable);

    /** 판정용 조회 — 주택형까지 한 번에 가져온다 (N+1 방지) */
    @Query("select distinct a from Announcement a left join fetch a.unitTypes "
            + "where a.receptEndDate >= :today")
    List<Announcement> findOpenWithUnitTypes(@Param("today") LocalDate today);

    @Query("select distinct a from Announcement a left join fetch a.unitTypes where a.id = :id")
    Optional<Announcement> findByIdWithUnitTypes(@Param("id") Long id);
}
