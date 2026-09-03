package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 여러 특별공급 유형에 공통으로 걸리는 요건 묶음 —
 * 재당첨 제한 · 소득 · 자산 · 청약통장 · 해당지역 거주.
 *
 * 규칙은 이걸 주입받아 {@link #checkAll} 한 뒤:
 *  - 모두 {@code passed()} 여야 자기 pass 조건 통과
 *  - 각 결과의 {@link RequirementCheck#describe} 로 사유를 결정에 추가
 * 규칙마다 소득·자산·재당첨 로직을 복붙하지 않기 위한 것이다(CLAUDE.md 절대 규칙 5).
 */
@Component
public class CommonRequirements {

    private final ReWinRequirement reWin;
    private final IncomeRequirement income;
    private final AssetRequirement asset;
    private final AccountRequirement account;
    private final RegionResidenceRequirement residence;

    public CommonRequirements(ReWinRequirement reWin, IncomeRequirement income,
                              AssetRequirement asset, AccountRequirement account,
                              RegionResidenceRequirement residence) {
        this.reWin = reWin;
        this.income = income;
        this.asset = asset;
        this.account = account;
        this.residence = residence;
    }

    public List<RequirementCheck> checkAll(ApplicantProfile profile,
                                           Announcement announcement,
                                           SpecialSupplyType type) {
        return List.of(
                reWin.check(profile, type),
                income.check(profile, type),
                asset.check(profile, announcement),
                account.check(profile, announcement),
                residence.check(profile, announcement));
    }

    /** 전부 통과했는지 */
    public static boolean allPassed(List<RequirementCheck> checks) {
        return checks.stream().allMatch(RequirementCheck::passed);
    }

    /** 사유를 결정에 옮긴다 */
    public static void describeAll(List<RequirementCheck> checks, EligibilityDecision decision) {
        checks.forEach(c -> c.describe(decision));
    }
}
