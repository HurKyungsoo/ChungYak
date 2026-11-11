package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.rule.rules.YouthRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청년 특별공급 경계값 — 금액은 2026-10 공고문 2건(제주 삼도이동·경기 광교) 표기 그대로:
 * 1인 140% 5,338,708원, 출생 1명 150% 5,720,045원, 총자산 본인 276백만원·부모 1,035백만원, 출생 1명 본인 311백만원.
 */
class YouthRuleTest {

    private final YouthRule rule = new YouthRule(RuleTestSupport.YOUTH, RuleTestSupport.REQUIREMENTS,
            RuleTestSupport.RE_WIN, RuleTestSupport.RESIDENCE);

    private static final Announcement JEJU = Announcement.builder()
            .externalId("2026000435").houseManageNo("2026000435").pblancNo("2026000435").houseName("삼도이동 2지구 토지임대부 공공분양주택")
            .houseType(HouseType.APT).houseDetailType(HouseDetailType.PUBLIC).regionName("제주")
            .regulationFlags(RegulationFlags.builder().publicHousingSpecialLaw(true).build())
            .build();

    /** 1인 가구 무주택 미혼 29세 — 가구 값이 곧 본인 값 */
    private static ApplicantProfile.ApplicantProfileBuilder single() {
        return ApplicantProfile.builder()
                .married(false).age(29).houseless(true)
                .accountMonths(12).accountPaymentCount(12)
                .householdSize(1).monthlyHouseholdIncome(4_000_000)
                .totalAssets(100_000_000L).carValue(0).parentsTotalAssets(500_000_000L);
    }

    private EligibilityDecision judge(ApplicantProfile p) {
        return rule.evaluate(p, JEJU);
    }

    @Test
    @DisplayName("기본 — 1인 가구 미혼 29세는 충족, 사유에 1인 가구 기준과 연도")
    void baseline() {
        EligibilityDecision d = judge(single().build());
        assertThat(d.isEligible()).isTrue();
        assertThat(d.getSatisfiedReasons()).anyMatch(r -> r.contains("1인 가구") && r.contains("140%") && r.contains("2026년도"));
    }

    @Test
    @DisplayName("나이 경계 19·39세 충족, 18·40세 미충족, 혼인 중이면 미충족")
    void age() {
        assertThat(judge(single().age(19).build()).isEligible()).isTrue();
        assertThat(judge(single().age(39).build()).isEligible()).isTrue();
        assertThat(judge(single().age(18).build()).isEligible()).isFalse();
        assertThat(judge(single().age(40).build()).isEligible()).isFalse();
        assertThat(judge(single().married(true).build()).getFailedReasons()).anyMatch(r -> r.contains("혼인 중"));
        assertThat(judge(single().age(null).build()).getMissingInputs()).contains("만 나이");
    }

    @Test
    @DisplayName("본인 소득 경계 — 1인 가구 140% = 5,338,708원, 출생 자녀 1명이면 150% = 5,720,045원")
    void income() {
        assertThat(judge(single().monthlyHouseholdIncome(5_338_708).build()).isEligible()).isTrue();
        assertThat(judge(single().monthlyHouseholdIncome(5_338_709).build()).isEligible()).isFalse();
        ApplicantProfile birth = single().monthlyHouseholdIncome(5_720_045).childCount(1).childrenBornSince20230328(1).build();
        assertThat(judge(birth).isEligible()).isTrue();
        assertThat(judge(single().monthlyHouseholdIncome(5_720_046).childCount(1).childrenBornSince20230328(1).build()).isEligible()).isFalse();
    }

    @Test
    @DisplayName("2인 이상 가구면 가구 소득을 본인 소득으로 쓰지 않는다 — 본인 소득을 따로 받는다")
    void personalIncomeNotGuessed() {
        EligibilityDecision d = judge(single().householdSize(3).monthlyHouseholdIncome(12_000_000).build());
        assertThat(d.getMissingInputs()).anyMatch(m -> m.contains("본인 월평균소득"));
        assertThat(judge(single().householdSize(3).monthlyHouseholdIncome(12_000_000).personalMonthlyIncome(3_000_000)
                .personalTotalAssets(100_000_000L).build()).isEligible()).isTrue();
    }

    @Test
    @DisplayName("총자산 경계 — 본인 276백만원·부모 1,035백만원, 출생 자녀 1명이면 본인 311백만원")
    void assets() {
        assertThat(judge(single().totalAssets(276_000_000L).build()).isEligible()).isTrue();
        assertThat(judge(single().totalAssets(276_000_001L).build()).isEligible()).isFalse();
        assertThat(judge(single().parentsTotalAssets(1_035_000_000L).build()).isEligible()).isTrue();
        assertThat(judge(single().parentsTotalAssets(1_035_000_001L).build()).getFailedReasons()).anyMatch(r -> r.contains("부모 총자산"));
        assertThat(judge(single().totalAssets(311_000_000L).childCount(1).childrenBornSince20230328(1).build()).isEligible()).isTrue();
        assertThat(judge(single().parentsTotalAssets(null).build()).getMissingInputs()).contains("부모 총자산");
    }

    @Test
    @DisplayName("1인 가구 — 가구 총자산 칸(자동차 제외)에 자동차가액을 더해 본인 총자산으로 본다, 자동차가액이 비면 MISSING")
    void singleHouseholdAddsCar() {
        // 2억 6,600만 + 자동차 1,000만 = 2억 7,600만 → 경계 통과, 1원 넘으면 탈락
        assertThat(judge(single().totalAssets(266_000_000L).carValue(10_000_000).build()).isEligible()).isTrue();
        assertThat(judge(single().totalAssets(266_000_001L).carValue(10_000_000).build()).getFailedReasons())
                .anyMatch(r -> r.contains("본인 총자산"));
        assertThat(judge(single().carValue(null).build()).getMissingInputs()).anyMatch(m -> m.contains("자동차가액"));
        // 본인 총자산을 직접 넣으면(자동차 포함 값) 그대로 쓴다 — 자동차가액을 또 더하지 않는다
        assertThat(judge(single().personalTotalAssets(276_000_000L).carValue(50_000_000).build()).isEligible()).isTrue();
    }

    @Test
    @DisplayName("무주택은 본인 기준 — 세대가 유주택(부모 집)이어도 본인 명의가 없으면 충족")
    void selfHouse() {
        assertThat(judge(single().houseless(false).selfNeverOwnedHouse(true).build()).isEligible()).isTrue();
        assertThat(judge(single().houseless(false).build()).isEligible()).isFalse();
        assertThat(judge(single().everOwnedHouse(true).build()).isEligible()).isFalse();
    }

    @Test
    @DisplayName("청약통장 6개월·6회 — 미달이면 개선 안내")
    void account() {
        assertThat(judge(single().accountMonths(6).accountPaymentCount(6).build()).isEligible()).isTrue();
        EligibilityDecision d = judge(single().accountMonths(5).build());
        assertThat(d.isEligible()).isFalse();
        assertThat(d.getImprovementHints()).isNotEmpty();
    }
}
