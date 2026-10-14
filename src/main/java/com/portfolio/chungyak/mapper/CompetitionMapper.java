package com.portfolio.chungyak.mapper;

import com.portfolio.chungyak.external.CompetitionRecords.Rate;
import com.portfolio.chungyak.external.CompetitionRecords.Score;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 당첨가점·경쟁률 기록 (#34) — MyBatis. 행이 수만 개라 JPA(IDENTITY 키는 배치 삽입이 안 된다) 대신 여러 행 INSERT.
 * 집계 조회는 지역·기간으로 공고와 이어 붙이는 동적 조회라 이쪽이 맞다(CLAUDE.md: MyBatis = 동적 조회·집계).
 */
@Mapper
public interface CompetitionMapper {

    @Select("SELECT COUNT(*) FROM competition_score")
    long countScores();

    @Delete("""
            <script>
            DELETE FROM competition_score WHERE house_manage_no IN
            <foreach item="h" collection="houses" open="(" separator="," close=")">#{h}</foreach>
            </script>""")
    int deleteScores(@Param("houses") Collection<String> houseManageNos);

    @Delete("""
            <script>
            DELETE FROM competition_rate WHERE house_manage_no IN
            <foreach item="h" collection="houses" open="(" separator="," close=")">#{h}</foreach>
            </script>""")
    int deleteRates(@Param("houses") Collection<String> houseManageNos);

    @Insert("""
            <script>
            INSERT INTO competition_score (house_manage_no, model_no, house_type, reside_code, lowest, average, highest) VALUES
            <foreach item="s" collection="rows" separator=",">
            (#{s.houseManageNo}, #{s.modelNo}, #{s.houseType}, #{s.resideCode}, #{s.lowest}, #{s.average}, #{s.highest})
            </foreach>
            </script>""")
    int insertScores(@Param("rows") List<Score> rows);

    @Insert("""
            <script>
            INSERT INTO competition_rate (house_manage_no, model_no, house_type, rank_code, reside_code, supply, requests, rate_text) VALUES
            <foreach item="r" collection="rows" separator=",">
            (#{r.houseManageNo}, #{r.modelNo}, #{r.houseType}, #{r.rankCode}, #{r.resideCode}, #{r.supply}, #{r.requests}, #{r.rateText})
            </foreach>
            </script>""")
    int insertRates(@Param("rows") List<Rate> rows);

    /** 비교 대상 공고 한 주택형의 기록 — 해당지역(01) 1순위 경쟁률과 최저가점(0 이면 0) */
    record PastUnit(String houseManageNo, LocalDate noticeDate, String houseType, int lowest, String rateText) {}

    /**
     * 같은 시도에서 [from, to) 사이에 나온 청약홈 민영 분양 공고(무순위·정정 전·취소 제외)의 주택형별 해당지역 기록.
     * 최저가점은 해당지역 행, 경쟁률은 1순위 해당지역 행 — 둘 중 하나만 있어도 나온다.
     */
    @Select("""
            SELECT a.house_manage_no AS houseManageNo, a.notice_date AS noticeDate, u.house_type AS houseType,
                   COALESCE(MAX(s.lowest), 0) AS lowest, MAX(r.rate_text) AS rateText
              FROM announcement a
              JOIN (SELECT house_manage_no, house_type FROM competition_score WHERE reside_code = '01'
                    UNION SELECT house_manage_no, house_type FROM competition_rate WHERE reside_code = '01' AND rank_code = 1) u
                ON u.house_manage_no = a.house_manage_no
              LEFT JOIN competition_score s
                ON s.house_manage_no = u.house_manage_no AND s.house_type = u.house_type AND s.reside_code = '01'
              LEFT JOIN competition_rate r
                ON r.house_manage_no = u.house_manage_no AND r.house_type = u.house_type AND r.reside_code = '01' AND r.rank_code = 1
             WHERE a.external_id NOT LIKE 'LH-%'
               AND a.region_name = #{region}
               AND a.house_detail_type = 'PRIVATE'
               AND (a.supply_category IS NULL OR a.supply_category <> 'REMAINDER')
               AND a.revision_state = 'CURRENT'
               AND a.notice_date >= #{from} AND a.notice_date < #{to}
             GROUP BY a.house_manage_no, a.notice_date, u.house_type""")
    List<PastUnit> pastUnits(@Param("region") String region, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
