package com.portfolio.chungyak.service;

import com.portfolio.chungyak.domain.*;
import com.portfolio.chungyak.repository.AnnouncementRepository;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import com.portfolio.chungyak.web.view.QuickCheckView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 3문항 빠른 진단.
 *
 * 이 서비스의 존재 이유는 "3문항으로 말할 수 있는 것만 말한다"이다 —
 * 탈락은 확정해도 자격은 확정하지 않는다. 그 경계를 테스트로 못박는다.
 */
class QuickCheckServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 9, 18).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private QuickCheckService serviceWith(Announcement... announcements) {
        AnnouncementRepository repo = mock(AnnouncementRepository.class);
        when(repo.findOpenWithUnitTypes(any())).thenReturn(List.of(announcements));
        return new QuickCheckService(
                new AnnouncementQueryService(repo, CLOCK),
                new EligibilityEngine(RuleTestSupport.allRules()));
    }

    private static Announcement announcement() {
        Announcement a = Announcement.builder()
                .externalId("A").houseManageNo("1").pblancNo("1").houseName("테스트 공고")
                .houseType(HouseType.APT).houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .receptBeginDate(LocalDate.now(CLOCK).plusDays(1))
                .receptEndDate(LocalDate.now(CLOCK).plusDays(5))
                .regulationFlags(RegulationFlags.builder().build())
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("084A")
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(47).multiChild(31).firstTime(22).build())
                .build());
        return a;
    }

    /** 3문항만 답한 프로필 — 소득·자산·통장은 비어 있다(= MISSING). */
    private static ApplicantProfile threeAnswers(boolean married, Integer marriageMonths, int children, boolean houseless) {
        return ApplicantProfile.builder()
                .married(married)
                .monthsSinceMarriage(marriageMonths)
                .childCount(children)
                .houseless(houseless)
                .build();
    }

    @Test
    @DisplayName("소득·자산을 안 물었으므로 어떤 유형도 '자격 있음'으로 나오지 않는다")
    void neverClaimsEligibility() {
        QuickCheckView view = serviceWith(announcement())
                .check(threeAnswers(true, 24, 0, true));

        // possible 은 "아직 탈락 사유가 없다"는 뜻일 뿐이라 남은 입력 항목이 반드시 있다
        assertThat(view.evaluated()).isTrue();
        assertThat(view.remainingInputs()).isNotEmpty();
    }

    @Test
    @DisplayName("자녀가 없으면 다자녀는 탈락 확정으로, 신혼부부는 가능성으로 분류된다")
    void rulesOutMultiChildButKeepsNewlywedPossible() {
        QuickCheckView view = serviceWith(announcement())
                .check(threeAnswers(true, 24, 0, true));

        assertThat(view.ruledOut()).extracting(QuickCheckView.TypeRow::typeLabel).contains("다자녀가구");
        assertThat(view.possible()).extracting(QuickCheckView.TypeRow::typeLabel).contains("신혼부부");
    }

    @Test
    @DisplayName("혼인 7년을 넘기면 신혼부부가 탈락 확정이 되고 사유가 함께 담긴다")
    void rulesOutNewlywedWhenMarriageTooLong() {
        QuickCheckView view = serviceWith(announcement())
                .check(threeAnswers(true, 100, 0, true));

        assertThat(view.ruledOut())
                .filteredOn(row -> row.typeLabel().equals("신혼부부"))
                .singleElement()
                .satisfies(row -> assertThat(row.reason()).isNotBlank());
    }

    @Test
    @DisplayName("3문항으로 판단할 수 없는 유형(노부모부양·신생아 등)은 결과에 넣지 않는다")
    void excludesTypesThatThreeQuestionsCannotJudge() {
        QuickCheckView view = serviceWith(announcement())
                .check(threeAnswers(true, 24, 0, true));

        List<String> shown = new java.util.ArrayList<>();
        view.possible().forEach(r -> shown.add(r.typeLabel()));
        view.ruledOut().forEach(r -> shown.add(r.typeLabel()));

        assertThat(shown).doesNotContain("노부모부양", "신생아", "기관추천", "청년");
    }

    @Test
    @DisplayName("접수중·접수예정 공고가 없으면 진단하지 않고 그대로 알린다")
    void notEvaluatedWhenNoAnnouncements() {
        QuickCheckView view = serviceWith().check(threeAnswers(true, 24, 0, true));

        assertThat(view.evaluated()).isFalse();
        assertThat(view.possible()).isEmpty();
        assertThat(view.ruledOut()).isEmpty();
    }
}
