package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties.AssetLimit;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties.IncomeLimit;
import com.portfolio.chungyak.rule.rules.FirstTimeRule;
import com.portfolio.chungyak.rule.rules.MultiChildRule;
import com.portfolio.chungyak.rule.rules.NewbornRule;
import com.portfolio.chungyak.rule.rules.NewlywedRule;
import com.portfolio.chungyak.rule.rules.OldParentsRule;

import java.util.List;
import java.util.Map;

/**
 * 규칙 테스트용 공통 배선 — 소득·자산 요건 컴포넌트를 application.yml 기본값으로 조립한다.
 * 규칙이 생성자 주입을 받게 되면서 테스트마다 이 세트를 새로 만들 필요가 없어졌다.
 */
public final class RuleTestSupport {

    private RuleTestSupport() {}

    /** 2024년 기준 도시근로자 가구원수별 월평균소득 (application.yml 과 동일) */
    public static final IncomeReference INCOME_REFERENCE = new IncomeReference(
            new IncomeReferenceProperties("2024", Map.of(
                    1, 3_482_964L, 2, 5_415_712L, 3, 7_198_649L, 4, 8_248_467L,
                    5, 8_775_071L, 6, 9_563_282L, 7, 10_351_493L, 8, 11_139_704L)));

    public static final SpecialSupplyRequirementProperties REQUIREMENTS =
            new SpecialSupplyRequirementProperties(
                    Map.of(
                            SpecialSupplyType.MULTI_CHILD, new IncomeLimit(120, 120),
                            SpecialSupplyType.NEWLYWED, new IncomeLimit(140, 160),
                            SpecialSupplyType.FIRST_TIME, new IncomeLimit(130, 160),
                            SpecialSupplyType.OLD_PARENTS, new IncomeLimit(120, 120),
                            SpecialSupplyType.NEWBORN, new IncomeLimit(150, 200)),
                    new AssetLimit(379_000_000L, 37_080_000));

    public static final IncomeRequirement INCOME = new IncomeRequirement(INCOME_REFERENCE, REQUIREMENTS);
    public static final AssetRequirement ASSET = new AssetRequirement(REQUIREMENTS);
    public static final ReWinRequirement RE_WIN = new ReWinRequirement(
            new ReWinRestrictionProperties(120, 60));
    public static final AccountRequirement ACCOUNT = new AccountRequirement(
            new AccountRequirementProperties(12, 24,
                    new AccountRequirementProperties.PrivateDeposit(3_000_000, 2_500_000, 2_000_000)));
    public static final RegionResidenceRequirement RESIDENCE = new RegionResidenceRequirement(
            new RegionResidenceRequirementProperties(24, 12));
    public static final CommonRequirements COMMON =
            new CommonRequirements(RE_WIN, INCOME, ASSET, ACCOUNT, RESIDENCE);

    public static List<EligibilityRule> allRules() {
        return List.of(
                new NewlywedRule(COMMON),
                new FirstTimeRule(COMMON),
                new MultiChildRule(COMMON),
                new OldParentsRule(COMMON),
                new NewbornRule(COMMON));
    }

    /**
     * 소득·자산 요건을 넉넉히 통과하는 프로필 빌더 (월소득 500만·3인 = 약 69%, 자산 여유).
     * 유형별 조건은 각 테스트가 이어서 설정한다.
     */
    public static ApplicantProfile.ApplicantProfileBuilder passingIncomeAndAssets() {
        return ApplicantProfile.builder()
                .monthlyHouseholdIncome(5_000_000)
                .householdSize(3)
                .totalAssets(200_000_000L)
                .carValue(15_000_000)
                .accountPaymentCount(24)           // 국민주택 규제지역 기준도 충족
                .accountDeposit(15_000_000)        // 민영 예치금 기준 충족
                .residenceMonthsInRegion(36);      // 규제지역 거주요건(24개월)도 충족
    }
}
