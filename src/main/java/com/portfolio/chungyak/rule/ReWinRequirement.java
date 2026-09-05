package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

/**
 * 재당첨 제한 확인 (주택공급규칙 제54조).
 *
 * 두 가지를 본다:
 *  1. 특별공급은 세대당 <b>평생 1회</b> — 과거 특공 당첨 이력이 있으면 모든 특공 신청 불가.
 *  2. 재당첨 제한 기간 — 마지막 당첨일로부터 지역별 제한 기간이 지나야 한다.
 */
@Component
public class ReWinRequirement {

    private final ReWinRestrictionProperties restriction;

    public ReWinRequirement(ReWinRestrictionProperties restriction) {
        this.restriction = restriction;
    }

    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type) {
        if (profile.isEverWonSpecialSupply()) {
            return RequirementCheck.fail(
                    "과거 특별공급 당첨 이력이 있어 특별공급은 다시 신청할 수 없습니다 (세대당 평생 1회).");
        }

        Integer months = profile.getMonthsSinceLastWin();
        if (months == null) {
            // pastWinInSpeculationArea 를 체크했다는 건 과거 당첨 이력이 있다는 뜻이다.
            // 그런데 경과 개월이 비어 있으면 "이력 없음"으로 단정할 근거가 없다 — 추측 대신 MISSING.
            if (profile.isPastWinInSpeculationArea()) {
                return RequirementCheck.missing("마지막 당첨일로부터 경과 개월");
            }
            return RequirementCheck.pass("과거 당첨 이력이 없습니다.");
        }

        int limit = restriction.monthsFor(profile.isPastWinInSpeculationArea());
        String scope = profile.isPastWinInSpeculationArea()
                ? "투기과열지구·청약과열지역 당첨" : "일반 지역 당첨";

        return months >= limit
                ? RequirementCheck.pass("마지막 당첨일로부터 " + months + "개월이 지나 재당첨 제한("
                        + scope + " " + limit + "개월)이 풀렸습니다.")
                : RequirementCheck.fail("마지막 당첨일로부터 " + months + "개월로 재당첨 제한 기간("
                        + scope + " " + limit + "개월)이 지나지 않았습니다.",
                        ImprovementHints.reWinMonths(months, limit));
    }
}
