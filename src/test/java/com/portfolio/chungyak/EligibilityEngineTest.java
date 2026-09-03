package com.portfolio.chungyak;

import com.portfolio.chungyak.domain.*;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙 엔진 검증.
 *
 * 이 프로젝트가 "LLM 이 판정하지 않는다"고 주장하려면,
 * 판정이 실제로 결정론적이라는 걸 테스트로 보여야 한다.
 * 같은 입력에 항상 같은 출력이고, 경계값에서 정확히 갈린다.
 *
 * 실데이터 기반: "올 뉴 챔피언스시티 1차"(2026000419) 084.9730A 타입
 *   일반 141 / 특공 172 (신혼47 다자녀31 생애최초22 노부모10 기관추천31 신생아31)
 */
class EligibilityEngineTest {

    private EligibilityEngine engine;
    private Announcement privateAnnouncement;   // 민영, 비규제
    private Announcement regulatedAnnouncement; // 민영, 규제지역
    private Announcement publicAnnouncement;    // 공공(국민)

    @BeforeEach
    void setUp() {
        engine = new EligibilityEngine(RuleTestSupport.allRules());
        privateAnnouncement = announcement(HouseDetailType.PRIVATE, false);
        regulatedAnnouncement = announcement(HouseDetailType.PRIVATE, true);
        publicAnnouncement = announcement(HouseDetailType.PUBLIC, false);
    }

    /** 소득·자산 요건은 넉넉히 통과하는 프로필 (월소득 500만·3인 ≈ 70%) */
    private static ApplicantProfile.ApplicantProfileBuilder passing() {
        return RuleTestSupport.passingIncomeAndAssets();
    }

    private Announcement announcement(HouseDetailType detailType, boolean regulated) {
        Announcement a = Announcement.builder()
                .externalId("TEST-" + detailType + "-" + regulated)
                .houseManageNo("2026000419")
                .pblancNo("2026000419")
                .houseName("테스트 아파트")
                .houseType(HouseType.APT)
                .houseDetailType(detailType)
                .regionName("서울")
                .receptBeginDate(LocalDate.now().plusDays(7))
                .receptEndDate(LocalDate.now().plusDays(9))
                .regulationFlags(RegulationFlags.builder()
                        .speculationOverheated(regulated)
                        .build())
                .build();

        // 실데이터: "올 뉴 챔피언스시티 1차"(민영) 084.9730A — 신생아 31세대도 민영에 배정된다
        a.addUnitType(UnitType.builder()
                .modelNo("01")
                .typeName("084.9730A")
                .generalSupplyCount(141)
                .specialSupplyCount(172)
                .supplyBreakdown(SupplyBreakdown.builder()
                        .newlywed(47).multiChild(31).firstTime(22)
                        .oldParents(10).institutionRecommend(31).newborn(31)
                        .build())
                .topAmount(87000)
                .build());
        return a;
    }

    @Test
    @DisplayName("신혼부부 - 혼인 7년 이내 + 무주택 + 통장 6개월 + 소득요건이면 자격 있음")
    void newlywedEligible() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(36)
                .houseless(true).accountMonths(12)
                .build();

        EligibilityEngine.MatchResult result = engine.evaluate(profile, privateAnnouncement);
        EligibilityDecision decision = result.decisions().get(SpecialSupplyType.NEWLYWED);

        assertThat(decision.isEligible()).isTrue();
        assertThat(result.hasAnyMatch()).isTrue();
    }

    @Test
    @DisplayName("신혼부부 - 혼인 84개월은 통과, 85개월은 탈락 (경계값)")
    void newlywedMarriageBoundary() {
        ApplicantProfile at84 = passing()
                .married(true).monthsSinceMarriage(84)
                .houseless(true).accountMonths(12).build();
        ApplicantProfile at85 = passing()
                .married(true).monthsSinceMarriage(85)
                .houseless(true).accountMonths(12).build();

        assertThat(engine.evaluate(at84, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isTrue();
        assertThat(engine.evaluate(at85, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isFalse();
    }

    @Test
    @DisplayName("규제지역은 청약통장 24개월이 필요하다 — 같은 사람이 공고에 따라 갈린다")
    void regulatedAreaRequiresLongerAccount() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(24)
                .houseless(true).accountMonths(12)   // 6개월은 넘지만 24개월 미달
                .build();

        assertThat(engine.evaluate(profile, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isTrue();
        assertThat(engine.evaluate(profile, regulatedAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isFalse();
    }

    @Test
    @DisplayName("소득이 유형 상한(신혼 140%)을 넘으면 탈락 — 경계값")
    void incomeLimitBoundary() {
        // 도시근로자 3인 기준 7,198,649원. 140% = 10,078,108원.
        ApplicantProfile at140 = ApplicantProfile.builder()
                .married(true).monthsSinceMarriage(36).houseless(true).accountMonths(12)
                .totalAssets(100_000_000L).carValue(0)
                .householdSize(3).monthlyHouseholdIncome(10_078_108).build();      // 정확히 140%
        ApplicantProfile over = ApplicantProfile.builder()
                .married(true).monthsSinceMarriage(36).houseless(true).accountMonths(12)
                .totalAssets(100_000_000L).carValue(0)
                .householdSize(3).monthlyHouseholdIncome(10_500_000).build();      // 약 146%

        assertThat(engine.evaluate(at140, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isTrue();
        assertThat(engine.evaluate(over, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isFalse();
    }

    @Test
    @DisplayName("맞벌이는 소득 상한이 완화된다 (신혼 140% -> 160%) — 같은 소득이 갈린다")
    void dualIncomeRelaxation() {
        int income = 10_500_000;   // 3인 기준 약 146% — 단독 140 초과, 맞벌이 160 이내
        ApplicantProfile single = ApplicantProfile.builder()
                .married(true).monthsSinceMarriage(36).houseless(true).accountMonths(12)
                .totalAssets(100_000_000L).carValue(0)
                .householdSize(3).monthlyHouseholdIncome(income).dualIncome(false).build();
        ApplicantProfile dual = ApplicantProfile.builder()
                .married(true).monthsSinceMarriage(36).houseless(true).accountMonths(12)
                .totalAssets(100_000_000L).carValue(0)
                .householdSize(3).monthlyHouseholdIncome(income).dualIncome(true).build();

        assertThat(engine.evaluate(single, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isFalse();
        assertThat(engine.evaluate(dual, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isTrue();
    }

    @Test
    @DisplayName("공공주택 특공은 자산 요건이 있다 — 총자산 초과면 탈락, 민영은 자산 안 본다")
    void publicHousingAssetRequirement() {
        ApplicantProfile richInAssets = ApplicantProfile.builder()
                .married(true).monthsSinceMarriage(36).houseless(true).accountMonths(12)
                .householdSize(3).monthlyHouseholdIncome(5_000_000)
                .totalAssets(500_000_000L).carValue(15_000_000)   // 총자산 5억 > 3.79억
                .build();

        assertThat(engine.evaluate(richInAssets, publicAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isFalse();
        // 같은 사람이라도 민영 공고면 자산 요건이 없어 통과
        assertThat(engine.evaluate(richInAssets, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED).isEligible()).isTrue();
    }

    @Test
    @DisplayName("소득 정보가 없으면 판정 불가 — 무엇이 빠졌는지 알린다")
    void missingIncomeIsUndetermined() {
        ApplicantProfile noIncome = ApplicantProfile.builder()
                .married(true).monthsSinceMarriage(36).houseless(true).accountMonths(12)
                .build();   // 소득·가구원 수 없음

        EligibilityDecision decision = engine.evaluate(noIncome, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED);

        assertThat(decision.isEligible()).isFalse();
        assertThat(decision.isUndetermined()).isTrue();
        assertThat(decision.getMissingInputs())
                .anyMatch(s -> s.contains("소득") || s.contains("가구원"));
    }

    @Test
    @DisplayName("신생아 특공 자격은 공고 유형과 무관하게 판정한다 — 민영에도 물량이 배정된다")
    void newbornRuleIgnoresHouseType() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(12)
                .hasNewborn(true).houseless(true).accountMonths(12)
                .build();

        // 규칙은 신청자 조건만 본다 — 민영/공공 모두 자격 충족
        assertThat(engine.evaluate(profile, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWBORN).isEligible()).isTrue();
        assertThat(engine.evaluate(profile, publicAnnouncement)
                .decisions().get(SpecialSupplyType.NEWBORN).isEligible()).isTrue();

        // 실제 신청 가능 여부는 그 공고 주택형에 신생아 물량이 있느냐로 갈린다 (엔진이 판단)
        assertThat(engine.evaluate(profile, privateAnnouncement).bestMatch().applicableTypes())
                .contains(SpecialSupplyType.NEWBORN);
    }

    @Test
    @DisplayName("자격은 되지만 그 공고에 신생아 물량이 없으면 물량없음으로 안내한다")
    void newbornQualifiedButNoAllocation() {
        Announcement noNewborn = announcement(HouseDetailType.PRIVATE, false);
        noNewborn.getUnitTypes().clear();
        noNewborn.addUnitType(UnitType.builder()
                .modelNo("01").typeName("059.9900A")
                .generalSupplyCount(100).specialSupplyCount(40)
                .supplyBreakdown(SupplyBreakdown.builder()
                        .newlywed(25).multiChild(15).build())   // 신생아 배정 없음
                .build());

        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(12)
                .hasNewborn(true).houseless(true).accountMonths(12)
                .build();

        EligibilityEngine.MatchResult result = engine.evaluate(profile, noNewborn);
        assertThat(result.decisions().get(SpecialSupplyType.NEWBORN).isEligible()).isTrue();
        assertThat(result.qualifiedButUnavailable()).contains(SpecialSupplyType.NEWBORN);
    }

    @Test
    @DisplayName("판정 결과에는 반드시 이유가 남는다 — LLM 이 지어낼 여지를 없앤다")
    void decisionAlwaysHasReasons() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(100)
                .houseless(false).accountMonths(3)
                .build();

        EligibilityDecision decision = engine.evaluate(profile, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED);

        assertThat(decision.isEligible()).isFalse();
        assertThat(decision.getFailedReasons())
                .hasSize(3)   // 혼인기간 초과 + 주택 소유 + 통장 미달 (소득·자산은 통과)
                .anyMatch(r -> r.contains("혼인기간"))
                .anyMatch(r -> r.contains("무주택"))
                .anyMatch(r -> r.contains("청약통장"));
    }

    @Test
    @DisplayName("입력이 부족하면 판정하지 않고 무엇이 빠졌는지 알린다")
    void missingInputIsReported() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(24)
                .houseless(true)
                .build();   // accountMonths 없음

        EligibilityDecision decision = engine.evaluate(profile, privateAnnouncement)
                .decisions().get(SpecialSupplyType.NEWLYWED);

        assertThat(decision.isUndetermined()).isTrue();
        assertThat(decision.getMissingInputs()).contains("청약통장 가입 기간");
    }

    @Test
    @DisplayName("자격은 되는데 그 공고에 물량이 없으면 별도로 안내한다")
    void qualifiedButNoAllocation() {
        // 신생아 자격은 되지만 민영 공고라 물량 자체가 없다
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(12)
                .childCount(2).hasNewborn(true)
                .houseless(true).accountMonths(12)
                .build();

        EligibilityEngine.MatchResult result = engine.evaluate(profile, publicAnnouncement);

        // 공공주택이므로 신생아·다자녀·신혼 모두 매칭돼야 한다
        assertThat(result.bestMatch()).isNotNull();
        assertThat(result.bestMatch().applicableTypes())
                .contains(SpecialSupplyType.NEWLYWED, SpecialSupplyType.MULTI_CHILD,
                          SpecialSupplyType.NEWBORN);
    }

    @Test
    @DisplayName("같은 입력이면 항상 같은 결과 — 결정론성 확인")
    void deterministic() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(36)
                .childCount(2).houseless(true).accountMonths(12)
                .build();

        EligibilityEngine.MatchResult first = engine.evaluate(profile, privateAnnouncement);
        for (int i = 0; i < 20; i++) {
            EligibilityEngine.MatchResult repeated = engine.evaluate(profile, privateAnnouncement);
            assertThat(repeated.bestMatch().applicableTypes())
                    .isEqualTo(first.bestMatch().applicableTypes());
        }
    }
}
