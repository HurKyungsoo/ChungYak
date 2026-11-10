package com.portfolio.chungyak.domain;

import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.IncomeReference;
import com.portfolio.chungyak.rule.IncomeReferenceProperties;
import com.portfolio.chungyak.rule.IncomeRequirement;
import com.portfolio.chungyak.rule.RuleTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 공고문의 입주자모집공고일(자격 판단 기준일) — 문장은 2026-11-10 LH 공고문 원문 그대로 */
class NoticeBaseDateTest {

    @Test
    @DisplayName("공고문 표기에서 입주자모집공고일을 읽는다")
    void readsBaseDate() {
        // 익산평화 공공분양 추가 입주자모집 — LH 목록 날짜는 2026-08-12 지만 공고문 기준일은 2025.03.05
        assertThat(NoticeBaseDate.find("■ 이 주택의 입주자모집공고일은 2025.03.05.(수)이며, 이는 신청자격(만 19세 이상 성인)의 판단기준일이 됩니다."))
                .isEqualTo(LocalDate.of(2025, 3, 5));
        assertThat(NoticeBaseDate.find("■ 입주자 모집공고일(2026.09.04.) 현재 대한민국에 거주하는"))
                .isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(NoticeBaseDate.find("입주자모집공고일 : 2026. 10. 30.")).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(NoticeBaseDate.find("■ 공고일 : 2026.11.05")).isNull();   // 일반매각 머리의 게시일은 보지 않는다
        assertThat(NoticeBaseDate.find("입주자모집공고일은 2026.13.40")).isNull();
        assertThat(NoticeBaseDate.find(null)).isNull();
    }

    @Test
    @DisplayName("기준일은 공고문 날짜 우선, 게시일보다 뒤면 잘못 읽은 것이라 버린다")
    void eligibilityBaseDate() {
        Announcement a = Announcement.builder().externalId("LH-1").houseName("t").noticeDate(LocalDate.of(2026, 8, 12)).build();
        assertThat(a.eligibilityBaseDate()).isEqualTo(LocalDate.of(2026, 8, 12));

        a.markNoticeBaseDate(LocalDate.of(2025, 3, 5));
        assertThat(a.eligibilityBaseDate()).isEqualTo(LocalDate.of(2025, 3, 5));

        Announcement b = Announcement.builder().externalId("LH-2").houseName("t").noticeDate(LocalDate.of(2026, 8, 12)).build();
        b.markNoticeBaseDate(LocalDate.of(2026, 9, 1));
        assertThat(b.eligibilityBaseDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    }

    @Test
    @DisplayName("해마다 바뀌는 기준은 게시일이 아니라 자격 판단 기준일로 고른다")
    void yearlyFigureUsesBaseDate() {
        IncomeReference ref = new IncomeReference(new IncomeReferenceProperties("2025", Map.of(3, 7_500_000L),
                LocalDate.of(2026, 3, 1), List.of(new IncomeReferenceProperties.Version("2024", null, Map.of(3, 6_000_000L)))));
        IncomeRequirement income = new IncomeRequirement(ref, RuleTestSupport.REQUIREMENTS);
        Announcement a = Announcement.builder().externalId("LH-1").houseName("t")
                .houseDetailType(HouseDetailType.PUBLIC).noticeDate(LocalDate.of(2026, 8, 12)).build();
        a.markNoticeBaseDate(LocalDate.of(2025, 3, 5));
        ApplicantProfile p = ApplicantProfile.builder().monthlyHouseholdIncome(7_500_000).householdSize(3).build();

        assertThat(income.check(p, com.portfolio.chungyak.domain.SpecialSupplyType.NEWLYWED, a).reason())
                .contains("2024년 기준").contains("125%");   // 게시일(2026-08)이었다면 2025년 표 100%
    }
}
