package com.portfolio.chungyak.web;

import com.portfolio.chungyak.domain.*;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.EligibilityEngine;
import com.portfolio.chungyak.rule.RuleTestSupport;
import com.portfolio.chungyak.web.view.EligibilityResultAssembler;
import com.portfolio.chungyak.web.view.EligibilityResultView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판정 결과 -> 화면 모델 변환 검증.
 *
 * 화면에 이유가 하나도 빠지지 않고 실리는지 확인한다.
 * (이유 없는 판정은 CLAUDE.md 절대 규칙 위반이고, 나중에 LLM 이 근거를 지어내게 된다.)
 */
class EligibilityResultAssemblerTest {

    private EligibilityEngine engine;
    private final EligibilityResultAssembler assembler = new EligibilityResultAssembler();

    @BeforeEach
    void setUp() {
        engine = new EligibilityEngine(RuleTestSupport.allRules());
    }

    /** 소득·자산 요건은 넉넉히 통과하는 프로필 (월소득 500만·3인 ≈ 70%) */
    private static ApplicantProfile.ApplicantProfileBuilder passing() {
        return RuleTestSupport.passingIncomeAndAssets();
    }

    private Announcement publicAnnouncement() {
        Announcement a = Announcement.builder()
                .externalId("TEST-PUBLIC")
                .houseManageNo("2026000419").pblancNo("2026000419")
                .houseName("테스트 국민주택")
                .houseType(HouseType.APT)
                .houseDetailType(HouseDetailType.PUBLIC)
                .regionName("서울")
                .receptBeginDate(LocalDate.now().plusDays(7))
                .receptEndDate(LocalDate.now().plusDays(9))
                .regulationFlags(RegulationFlags.builder().build())
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("084.9730A")
                .generalSupplyCount(141).specialSupplyCount(172)
                .supplyBreakdown(SupplyBreakdown.builder()
                        .newlywed(47).multiChild(31).firstTime(22)
                        .oldParents(10).institutionRecommend(31).newborn(31)
                        .build())
                .build());
        return a;
    }

    @Test
    @DisplayName("신청 가능한 주택형에 유형별 배정 세대수가 그대로 담긴다")
    void carriesAllocationCounts() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(24)
                .childCount(2).hasNewborn(true)
                .houseless(true).accountMonths(12)
                .build();

        EligibilityResultView view = assembler.assemble(engine.evaluate(profile, publicAnnouncement()));

        assertThat(view.hasAnyMatch()).isTrue();
        assertThat(view.matchedUnitTypes()).hasSize(1);
        EligibilityResultView.MatchedUnitType m = view.matchedUnitTypes().get(0);
        assertThat(m.applicableTypes())
                .extracting(EligibilityResultView.AllocatedType::typeLabel)
                .contains("신혼부부", "다자녀가구", "생애최초", "신생아");
        // 신혼47 + 다자녀31 + 생애최초22 + 신생아31 (노부모부양은 자격 미달)
        assertThat(m.totalAllocated()).isEqualTo(47 + 31 + 22 + 31);
    }

    @Test
    @DisplayName("모든 판정에 이유(satisfied/failed/missing 중 최소 하나)가 실린다")
    void everyDecisionHasReasons() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(100)   // 혼인기간 초과
                .houseless(false)                          // 주택 보유
                .build();                                  // accountMonths 없음

        EligibilityResultView view = assembler.assemble(engine.evaluate(profile, publicAnnouncement()));

        assertThat(view.allDecisions()).isNotEmpty();
        for (EligibilityResultView.TypeDecision d : view.allDecisions()) {
            int total = d.satisfiedReasons().size() + d.failedReasons().size() + d.missingInputs().size();
            assertThat(total)
                    .as("판정 '%s' 에 이유가 하나도 없다", d.typeLabel())
                    .isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("자격은 되지만 이 공고에 물량이 없는 유형이 별도 목록으로 나온다")
    void listsQualifiedButUnavailable() {
        // 신혼 배정만 있고 다자녀 배정은 없는 공고
        Announcement a = Announcement.builder()
                .externalId("TEST-NEWLYWED-ONLY")
                .houseManageNo("X").pblancNo("X")
                .houseName("신혼 물량만 있는 공고")
                .houseType(HouseType.APT)
                .houseDetailType(HouseDetailType.PRIVATE)
                .regionName("서울")
                .receptBeginDate(LocalDate.now().plusDays(3))
                .receptEndDate(LocalDate.now().plusDays(5))
                .regulationFlags(RegulationFlags.builder().build())
                .build();
        a.addUnitType(UnitType.builder()
                .modelNo("01").typeName("059.9900")
                .supplyBreakdown(SupplyBreakdown.builder().newlywed(20).build())
                .build());

        // 신혼·다자녀 둘 다 자격이 되는 신청자
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(24)
                .childCount(2)
                .houseless(true).accountMonths(12)
                .build();

        EligibilityResultView view = assembler.assemble(engine.evaluate(profile, a));

        assertThat(view.qualifiedButUnavailable())
                .extracting(EligibilityResultView.TypeDecision::typeLabel)
                .contains("다자녀가구")
                .doesNotContain("신혼부부");
        assertThat(view.qualifiedButUnavailable())
                .allSatisfy(d -> {
                    assertThat(d.eligible()).isTrue();
                    assertThat(d.satisfiedReasons()).isNotEmpty();
                });
    }

    @Test
    @DisplayName("commonChecks 는 재당첨·소득·자산·통장·거주 5개 라벨을 순서대로 담고, own* 목록엔 안 겹친다")
    void splitsCommonChecksFromOwnReasons() {
        // 혼인기간(신혼부부 고유 요건)만 초과시키고 공통요건(소득·자산 등)은 넉넉히 통과시킨다.
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(100)   // 84개월 초과 — NewlywedRule 자체 요건
                .houseless(true).accountMonths(24)
                .build();

        EligibilityResultView view = assembler.assemble(engine.evaluate(profile, publicAnnouncement()));
        EligibilityResultView.TypeDecision newlywed = view.allDecisions().stream()
                .filter(d -> d.typeLabel().equals("신혼부부"))
                .findFirst().orElseThrow();

        assertThat(newlywed.commonChecks())
                .extracting(c -> c.kindLabel())
                .containsExactly("재당첨 제한", "소득", "자산", "청약통장", "거주");
        // 혼인기간 초과는 이 유형만의 이유라 own 목록에 남아야 한다.
        assertThat(newlywed.ownFailedReasons()).anyMatch(r -> r.contains("혼인기간"));
        // 공통요건(소득 등)은 통과했으니 own 목록엔 안 들어간다 — 위쪽 요약 레일에서만 보여준다.
        assertThat(newlywed.ownSatisfiedReasons()).noneMatch(r -> r.contains("도시근로자"));
        assertThat(newlywed.satisfiedReasons()).anyMatch(r -> r.contains("도시근로자"));
    }

    @Test
    @DisplayName("카드별 '이 공고 배정 N세대' = 주택형 합계, 세대수를 모르는 공고(LH)는 null")
    void allocatedInAnnouncement() {
        ApplicantProfile profile = passing().married(true).monthsSinceMarriage(24).houseless(true).accountMonths(24).build();

        EligibilityResultView view = assembler.assemble(engine.evaluate(profile, publicAnnouncement()));
        assertThat(view.allDecisions()).filteredOn(d -> d.typeLabel().equals("신혼부부"))
                .extracting(EligibilityResultView.TypeDecision::allocatedInAnnouncement).containsExactly(47);
        // 신혼희망타운은 일반 공고에 배정이 없다 — null(모름)이 아니라 0(없음)
        assertThat(view.allDecisions()).filteredOn(d -> d.typeName().equals("NEWLYWED_HOPE_TOWN"))
                .extracting(EligibilityResultView.TypeDecision::allocatedInAnnouncement).containsExactly(0);

        Announcement lh = Announcement.builder().externalId("LH-1").houseManageNo("03").pblancNo("1")
                .houseName("LH 국민임대").houseType(HouseType.UNKNOWN).houseDetailType(HouseDetailType.PUBLIC)
                .regulationFlags(RegulationFlags.builder().build()).build();
        lh.addUnitType(UnitType.builder().modelNo("01").typeName("26A")
                .supplyBreakdown(SupplyBreakdown.ofPresentTypes(java.util.Set.of(SpecialSupplyType.NEWLYWED))).build());
        EligibilityResultView lhView = assembler.assemble(engine.evaluate(profile, lh));
        assertThat(lhView.allDecisions()).allSatisfy(d -> assertThat(d.allocatedInAnnouncement()).isNull());
    }

    @Test
    @DisplayName("fixable 은 엔진 판정 그대로 옮긴다 — 통장만 모자라면 참, 혼인 7년 초과가 섞이면 거짓")
    void carriesEngineFixable() {
        var onlyAccount = assembler.assemble(engine.evaluate(passing().married(true).monthsSinceMarriage(24)
                .houseless(true).accountMonths(3).build(), publicAnnouncement()));
        var mixed = assembler.assemble(engine.evaluate(passing().married(true).monthsSinceMarriage(100)
                .houseless(true).accountMonths(3).build(), publicAnnouncement()));

        assertThat(onlyAccount.allDecisions()).filteredOn(d -> d.typeName().equals("NEWLYWED"))
                .extracting(EligibilityResultView.TypeDecision::fixable).containsExactly(true);
        assertThat(mixed.allDecisions()).filteredOn(d -> d.typeName().equals("NEWLYWED"))
                .extracting(EligibilityResultView.TypeDecision::fixable).containsExactly(false);
    }

    @Test
    @DisplayName("근거 카드 순서: 충족 → 정보 필요 → 미충족 (판정 자체는 그대로, 개수도 그대로)")
    void ordersCardsEligibleFirst() {
        ApplicantProfile profile = passing()
                .married(true).monthsSinceMarriage(100)   // 신혼부부 미충족
                .hasNewborn(true)                          // 신생아 충족
                .houseless(true).accountMonths(24)
                .build();                                  // childCount 없음 → 다자녀 정보 필요

        var result = engine.evaluate(profile, publicAnnouncement());
        EligibilityResultView view = assembler.assemble(result);

        assertThat(view.allDecisions()).hasSize(result.decisions().size());
        List<Integer> ranks = view.allDecisions().stream()
                .map(d -> d.eligible() ? 0 : d.undetermined() ? 1 : 2)
                .toList();
        assertThat(ranks).isSorted();
        assertThat(ranks).contains(0, 2);
    }
}
