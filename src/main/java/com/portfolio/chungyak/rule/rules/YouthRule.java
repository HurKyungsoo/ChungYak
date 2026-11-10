package com.portfolio.chungyak.rule.rules;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.ApplicantProfile;
import com.portfolio.chungyak.rule.AssetRequirement;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityRule;
import com.portfolio.chungyak.rule.ImprovementHints;
import com.portfolio.chungyak.rule.IncomeRequirement;
import com.portfolio.chungyak.rule.ReWinRequirement;
import com.portfolio.chungyak.rule.RegionResidenceRequirement;
import com.portfolio.chungyak.rule.RequirementCheck;
import com.portfolio.chungyak.rule.SpecialSupplyRequirementProperties;
import com.portfolio.chungyak.rule.YearlyFigure;
import com.portfolio.chungyak.rule.YouthSupplyProperties;
import org.springframework.stereotype.Component;

/**
 * 청년 특별공급 (공공주택 특별법 공공분양 — 청약홈 데이터상 공공주택에만 배정된다).
 *
 * 2026-10 공고문 2건 원문 그대로({@link YouthSupplyProperties}):
 *  - 19세 이상~39세 이하로 혼인 중이 아닐 것
 *  - 과거 주택을 소유한 사실이 없는 무주택자 — <b>본인 기준</b>(세대의 다른 구성원이 주택을 가져도 무주택자로 본다)
 *  - 입주자저축 가입 6개월 경과, 6회 이상 납입
 *  - <b>본인</b> 월평균소득 ≤ 도시근로자 <b>1인 가구</b> 월평균소득의 140% (2023-03-28 이후 출생 자녀 +10/+20%p)
 *  - 총자산: 본인·부모 각각 상한 이하
 *  - 재당첨 제한·해당지역 거주는 다른 유형과 같다
 *
 * 소득·자산이 세대가 아니라 본인·부모 기준이라 {@code CommonRequirements} 를 통째로 쓰지 않고 재당첨·거주만 골라 쓴다
 * ({@link NewlywedHopeTownRule} 과 같은 방식). 빈 입력은 추측하지 않는다 — 1인 가구일 때만 가구 값을 본인 값으로 본다.
 */
@Component
public class YouthRule implements EligibilityRule {

    /** 입주자저축 6개월·6회 — 공고문 "가입하여 6개월이 경과되고 … 6회 이상 납입" */
    private static final int MIN_ACCOUNT_MONTHS = 6;
    private static final int MIN_ACCOUNT_PAYMENT_COUNT = 6;

    private final YouthSupplyProperties properties;
    private final SpecialSupplyRequirementProperties requirements;
    private final ReWinRequirement reWinRequirement;
    private final RegionResidenceRequirement regionResidenceRequirement;

    public YouthRule(YouthSupplyProperties properties, SpecialSupplyRequirementProperties requirements,
                     ReWinRequirement reWinRequirement, RegionResidenceRequirement regionResidenceRequirement) {
        this.properties = properties;
        this.requirements = requirements;
        this.reWinRequirement = reWinRequirement;
        this.regionResidenceRequirement = regionResidenceRequirement;
    }

    @Override
    public SpecialSupplyType supportedType() {
        return SpecialSupplyType.YOUTH;
    }

    @Override
    public EligibilityDecision evaluate(ApplicantProfile profile, Announcement announcement) {
        if (profile.isMarried()) {
            return EligibilityDecision.ineligible(supportedType())
                    .failed("혼인 중이라 청년 특별공급 대상이 아닙니다(혼인 중이 아닌 19~39세만 신청할 수 있습니다).");
        }
        var amounts = properties.amountsFor(announcement == null ? null : announcement.eligibilityBaseDate());

        RequirementCheck age = checkAge(profile);
        RequirementCheck house = checkHouse(profile);
        RequirementCheck accountMonths = checkAccountMonths(profile);
        RequirementCheck accountCount = checkAccountPaymentCount(profile);
        RequirementCheck income = checkIncome(profile, amounts);
        RequirementCheck asset = checkAsset(profile, amounts);
        RequirementCheck reWin = reWinRequirement.check(profile, supportedType());
        RequirementCheck residence = regionResidenceRequirement.check(profile, announcement);

        boolean pass = age.passed() && house.passed() && accountMonths.passed() && accountCount.passed()
                && income.passed() && asset.passed() && reWin.passed() && residence.passed();
        EligibilityDecision decision = pass
                ? EligibilityDecision.eligible(supportedType())
                : EligibilityDecision.ineligible(supportedType());
        decision.satisfied("혼인 중이 아닙니다.");
        age.describe(decision);
        house.describe(decision);
        accountMonths.describe(decision);
        accountCount.describe(decision);
        income.describe(decision);
        asset.describe(decision);
        reWin.describe(decision);
        residence.describe(decision);
        return decision;
    }

    private RequirementCheck checkAge(ApplicantProfile profile) {
        Integer age = profile.getAge();
        if (age == null) return RequirementCheck.missing("만 나이");
        int min = properties.ageMin(), max = properties.ageMax();
        return age >= min && age <= max
                ? RequirementCheck.pass("나이 " + age + "세로 요건(" + min + "~" + max + "세)을 충족합니다.")
                : RequirementCheck.fail("나이 " + age + "세로 요건(" + min + "~" + max + "세)에 해당하지 않습니다.");
    }

    /** 본인 기준 — 세대가 무주택이고 소유 이력도 없으면 본인도 그렇다. 아니면 본인 명의 확인이 있어야 한다 */
    private RequirementCheck checkHouse(ApplicantProfile profile) {
        if (profile.isSelfNeverOwnedHouse()) {
            return RequirementCheck.pass("본인 명의로 주택을 가진 적이 없습니다(청년 특별공급은 본인만 봅니다).");
        }
        if (profile.isHouseless() && !profile.isEverOwnedHouse()) {
            return RequirementCheck.pass("무주택이고 과거 주택 소유 이력이 없습니다.");
        }
        return RequirementCheck.fail("본인 명의 주택 소유 이력이 없는지 확인되지 않았습니다 — 청년 특별공급은 본인만 보므로 부모 등 "
                + "세대원이 주택을 가졌어도 본인 명의가 아니면 '본인 명의로 주택을 가진 적이 없다'를 체크하세요.");
    }

    private RequirementCheck checkAccountMonths(ApplicantProfile profile) {
        Integer months = profile.getAccountMonths();
        if (months == null) return RequirementCheck.missing("청약통장 가입 기간");
        return months >= MIN_ACCOUNT_MONTHS
                ? RequirementCheck.pass("청약통장 가입 " + months + "개월로 요건(" + MIN_ACCOUNT_MONTHS + "개월)을 충족합니다.")
                : RequirementCheck.fail("청약통장 가입기간이 " + months + "개월로 요건(" + MIN_ACCOUNT_MONTHS + "개월)에 미달합니다.",
                        ImprovementHints.accountMonths(months, MIN_ACCOUNT_MONTHS));
    }

    private RequirementCheck checkAccountPaymentCount(ApplicantProfile profile) {
        Integer count = profile.getAccountPaymentCount();
        if (count == null) return RequirementCheck.missing("청약통장 납입 인정 횟수");
        return count >= MIN_ACCOUNT_PAYMENT_COUNT
                ? RequirementCheck.pass("청약통장 납입 " + count + "회로 요건(" + MIN_ACCOUNT_PAYMENT_COUNT + "회)을 충족합니다.")
                : RequirementCheck.fail("청약통장 납입 " + count + "회로 요건(" + MIN_ACCOUNT_PAYMENT_COUNT + "회)에 미달합니다.",
                        ImprovementHints.paymentCount(count, MIN_ACCOUNT_PAYMENT_COUNT));
    }

    /** 본인 소득 ≤ 1인 가구 월평균소득 × (140% + 출생 가산) */
    private RequirementCheck checkIncome(ApplicantProfile profile, YearlyFigure.Pick<YouthSupplyProperties.Amounts> amounts) {
        Integer personal = profile.getPersonalMonthlyIncome();
        if (personal == null && Integer.valueOf(1).equals(profile.getHouseholdSize())) personal = profile.getMonthlyHouseholdIncome();
        if (personal == null) return RequirementCheck.missing("본인 월평균소득(청년 특별공급은 본인 소득만 봅니다)");

        int tier = IncomeRequirement.birthTier(profile);
        int bonus = tier == 0 ? 0 : tier == 1 ? requirements.birthBonus().one() : requirements.birthBonus().twoOrMore();
        int percent = properties.incomePercent() + bonus;
        long limit = Math.round(amounts.value().singlePersonMonthlyAverage() * percent / 100.0);
        String bound = "도시근로자 1인 가구 월평균소득의 " + percent + "%(" + AssetRequirement.won(limit) + ", " + amounts.basis() + " 기준"
                + (bonus > 0 ? ", 2023.3.28 이후 출생 자녀 +" + bonus + "%p" : "") + ")";
        return personal <= limit
                ? RequirementCheck.pass("본인 월평균소득 " + AssetRequirement.won(personal) + "으로 " + bound + " 이하입니다." + amounts.note())
                : RequirementCheck.fail("본인 월평균소득 " + AssetRequirement.won(personal) + "이 " + bound + "을 넘습니다 — 월 "
                        + AssetRequirement.won(personal - limit) + " 초과." + amounts.note());
    }

    /** 총자산 — 본인·부모 각각 */
    private RequirementCheck checkAsset(ApplicantProfile profile, YearlyFigure.Pick<YouthSupplyProperties.Amounts> amounts) {
        Long self = profile.getPersonalTotalAssets();
        if (self == null && Integer.valueOf(1).equals(profile.getHouseholdSize())) self = profile.getTotalAssets();
        Long parents = profile.getParentsTotalAssets();
        if (self == null && parents == null) return RequirementCheck.missing("본인 총자산·부모 총자산(청년 특별공급은 각각 봅니다)");
        if (self == null) return RequirementCheck.missing("본인 총자산");
        if (parents == null) return RequirementCheck.missing("부모 총자산");

        var a = amounts.value();
        int tier = IncomeRequirement.birthTier(profile);
        long selfLimit = a.selfAssetLimit(tier);
        String relaxed = tier > 0 ? ", 2023.3.28 이후 출생 자녀 완화 기준" : "";
        String basis = amounts.basis() + " 기준" + relaxed;
        if (self > selfLimit) {
            return RequirementCheck.fail("본인 총자산 " + AssetRequirement.won(self) + "이 요건(" + AssetRequirement.won(selfLimit) + " 이하, "
                    + basis + ")을 초과합니다 — " + AssetRequirement.won(self - selfLimit) + " 초과." + amounts.note());
        }
        if (parents > a.parentsAssetLimit()) {
            return RequirementCheck.fail("부모 총자산 " + AssetRequirement.won(parents) + "이 요건(" + AssetRequirement.won(a.parentsAssetLimit())
                    + " 이하, " + amounts.basis() + " 기준)을 초과합니다." + amounts.note());
        }
        return RequirementCheck.pass("총자산이 본인 " + AssetRequirement.won(self) + "(" + AssetRequirement.won(selfLimit) + " 이하)·부모 "
                + AssetRequirement.won(parents) + "(" + AssetRequirement.won(a.parentsAssetLimit()) + " 이하)으로 요건을 충족합니다("
                + basis + ")." + amounts.note());
    }
}
