package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.AssetRequirement;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import com.portfolio.chungyak.rule.ImprovementHints;
import com.portfolio.chungyak.rule.IncomeRequirement;
import com.portfolio.chungyak.rule.NewlywedHopeTownProperties;
import com.portfolio.chungyak.rule.RegionResidenceRequirement;
import com.portfolio.chungyak.rule.ReWinRequirement;
import com.portfolio.chungyak.rule.RequirementCheck;
import org.springframework.stereotype.Component;

/**
 * 신혼희망타운(HOUSE_SECD=10) 특별공급.
 *
 * 표준 신혼부부 특공({@link NewlywedRule})과는 별도 제도다 — 자격기준이 다르다:
 *  - 혼인기간 7년 이내 <b>또는</b> 만 6세 이하 자녀(태아 포함) 있음 (둘 중 하나면 충분)
 *  - 무주택 세대구성원
 *  - 청약통장 가입 6개월 이상 <b>그리고</b> 납입인정횟수 6회 이상 (규제지역 구분 없음)
 *  - 소득: 도시근로자 월평균소득 130% 이하(맞벌이 200%) — {@code SpecialSupplyType.NEWLYWED_HOPE_TOWN}
 *    키로 {@link IncomeRequirement} 재사용(소득표 구조가 같아 별도 클래스 불필요)
 *  - 자산: 총자산+자동차가액 합산 3.62억원 이하(2026년 기준) — 표준 공공주택 자산기준과 별개라
 *    {@link NewlywedHopeTownProperties} 로 따로 관리
 *  - 재당첨 제한·거주요건은 특별공급 공통 규정이라 {@link ReWinRequirement}·
 *    {@link RegionResidenceRequirement} 만 재사용한다(소득·자산은 위처럼 신혼희망타운 전용 계산이라
 *    {@code CommonRequirements} 를 통째로 쓰지 않고 필요한 두 컴포넌트만 직접 주입받는다).
 *
 * 주의: 예비신혼부부(미혼, 공고일로부터 1년 이내 혼인 예정)·한부모가족 자격은 이 폼이
 * 표현할 방법이 없어 다루지 않는다 — "혼인 중"이 아니면 무조건 FAIL (NewlywedRule과 같은 제약).
 * 출처: myhome.go.kr 신혼희망타운 안내(2026-09-05 확인). 제도가 바뀌면 재확인할 것.
 */
@Component
public class NewlywedHopeTownRule implements EligibilityRule {

    private static final int MAX_MARRIAGE_MONTHS = 84;   // 7년
    private static final int MIN_ACCOUNT_MONTHS = 6;
    private static final int MIN_ACCOUNT_PAYMENT_COUNT = 6;

    private final IncomeRequirement incomeRequirement;
    private final ReWinRequirement reWinRequirement;
    private final RegionResidenceRequirement regionResidenceRequirement;
    private final NewlywedHopeTownProperties properties;

    public NewlywedHopeTownRule(IncomeRequirement incomeRequirement,
                                ReWinRequirement reWinRequirement,
                                RegionResidenceRequirement regionResidenceRequirement,
                                NewlywedHopeTownProperties properties) {
        this.incomeRequirement = incomeRequirement;
        this.reWinRequirement = reWinRequirement;
        this.regionResidenceRequirement = regionResidenceRequirement;
        this.properties = properties;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.NEWLYWED_HOPE_TOWN;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        if (!profile.isMarried()) {
            return EligibilityDecision.ineligible(supportedType())
                    .failed("혼인 상태가 아닙니다. 이 서비스는 신혼희망타운의 예비신혼부부·"
                            + "한부모가족 자격은 다루지 않고, 혼인 중인 신혼부부만 판정합니다.");
        }

        RequirementCheck age = checkAge(profile);
        RequirementCheck houseless = checkHouseless(profile);
        RequirementCheck accountMonths = checkAccountMonths(profile);
        RequirementCheck accountCount = checkAccountPaymentCount(profile);
        RequirementCheck asset = checkAsset(profile);
        RequirementCheck income = incomeRequirement.check(profile, supportedType());
        RequirementCheck reWin = reWinRequirement.check(profile, supportedType());
        RequirementCheck residence = regionResidenceRequirement.check(profile, announcement);

        boolean pass = age.passed() && houseless.passed() && accountMonths.passed()
                && accountCount.passed() && asset.passed()
                && income.passed() && reWin.passed() && residence.passed();

        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());

        age.describe(decision);
        houseless.describe(decision);
        accountMonths.describe(decision);
        accountCount.describe(decision);
        asset.describe(decision);
        income.describe(decision);
        reWin.describe(decision);
        residence.describe(decision);

        return decision;
    }

    /** 혼인기간 7년 이내 또는 6세 이하 자녀 — 둘 중 하나면 충분하다. */
    private RequirementCheck checkAge(ApplicantProfile profile) {
        Integer months = profile.getMonthsSinceMarriage();
        boolean marriedWithinLimit = months != null && months <= MAX_MARRIAGE_MONTHS;

        if (marriedWithinLimit) {
            return RequirementCheck.pass("혼인기간 " + months + "개월로 7년 이내입니다.");
        }
        if (profile.isHasChildUnderSix()) {
            return RequirementCheck.pass("혼인기간은 7년을 초과했지만 만 6세 이하 자녀가 있어 요건을 충족합니다.");
        }
        if (months == null) {
            return RequirementCheck.missing("혼인신고일로부터 경과 기간 또는 6세 이하 자녀 유무");
        }
        return RequirementCheck.fail("혼인기간이 " + months + "개월로 7년(84개월)을 초과했고 6세 이하 자녀도 없습니다.");
    }

    private RequirementCheck checkHouseless(ApplicantProfile profile) {
        return profile.isHouseless()
                ? RequirementCheck.pass("무주택 세대구성원 요건을 충족합니다.")
                : RequirementCheck.fail("주택을 소유하고 있어 무주택 요건을 충족하지 못합니다.");
    }

    private RequirementCheck checkAccountMonths(ApplicantProfile profile) {
        Integer months = profile.getAccountMonths();
        if (months == null) {
            return RequirementCheck.missing("청약통장 가입 기간");
        }
        if (months >= MIN_ACCOUNT_MONTHS) {
            return RequirementCheck.pass("청약통장 가입 " + months + "개월로 요건("
                    + MIN_ACCOUNT_MONTHS + "개월)을 충족합니다.");
        }
        return RequirementCheck.fail("청약통장 가입기간이 " + months + "개월로 요건("
                        + MIN_ACCOUNT_MONTHS + "개월)에 미달합니다.",
                ImprovementHints.accountMonths(months, MIN_ACCOUNT_MONTHS));
    }

    private RequirementCheck checkAccountPaymentCount(ApplicantProfile profile) {
        Integer count = profile.getAccountPaymentCount();
        if (count == null) {
            return RequirementCheck.missing("청약통장 납입 인정 횟수");
        }
        if (count >= MIN_ACCOUNT_PAYMENT_COUNT) {
            return RequirementCheck.pass("청약통장 납입 " + count + "회로 요건("
                    + MIN_ACCOUNT_PAYMENT_COUNT + "회)을 충족합니다.");
        }
        return RequirementCheck.fail("청약통장 납입 " + count + "회로 요건("
                        + MIN_ACCOUNT_PAYMENT_COUNT + "회)에 미달합니다.",
                ImprovementHints.paymentCount(count, MIN_ACCOUNT_PAYMENT_COUNT));
    }

    /** 총자산+자동차가액 합산 상한 — LH 공식 산정식(부동산+금융+기타+자동차-부채)과 같은 모양. */
    private RequirementCheck checkAsset(ApplicantProfile profile) {
        Long limit = properties.assetLimit();
        if (limit == null) {
            return RequirementCheck.pass("신혼희망타운 자산 상한이 설정돼 있지 않습니다.");
        }

        Long totalAssets = profile.getTotalAssets();
        Integer carValue = profile.getCarValue();
        if (totalAssets == null || carValue == null) {
            return RequirementCheck.missing("총자산·자동차가액(신혼희망타운은 합산해서 봅니다)");
        }

        long combined = totalAssets + carValue;
        if (combined <= limit) {
            return RequirementCheck.pass("총자산+자동차가액 " + AssetRequirement.won(combined)
                    + "으로 신혼희망타운 자산 요건(" + AssetRequirement.won(limit) + " 이하)을 충족합니다.");
        }
        return RequirementCheck.fail("총자산+자동차가액 " + AssetRequirement.won(combined)
                + "이 신혼희망타운 자산 요건(" + AssetRequirement.won(limit) + " 이하)을 초과합니다.");
    }
}
