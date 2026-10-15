package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * 특별공급 횟수 제한(주택공급규칙 제55조)과 재당첨 제한(제54조) 확인.
 *
 * 두 가지를 본다:
 *  1. 특별공급은 <b>한 차례에 한정하여 1세대 1주택</b>(제55조) — 과거 특공 당첨 이력이 있으면 다시 신청할 수 없다.
 *     단, 제55조의3(2025. 3. 31. 개정, 2026. 6. 15. 현행)의 특례가 있다 — {@link #countExemption}.
 *  2. 재당첨 제한 기간 — 마지막 당첨일로부터 지역별 제한 기간이 지나야 한다.
 */
@Component
public class ReWinRequirement {

    /** 배우자의 혼인 전 당첨 이력을 따지지 않는 유형 — 제55조의3제1항(제35조의3 신생아, 제41조 신혼부부, 제43조 생애최초) */
    static final Set<SpecialSupplyType> SPOUSE_PRE_MARRIAGE_IGNORED =
            EnumSet.of(SpecialSupplyType.NEWBORN, SpecialSupplyType.NEWLYWED, SpecialSupplyType.FIRST_TIME);

    /** 2024. 6. 19. 이후 출생 자녀가 있으면 한 차례 더 — 제55조의3제3항(제35조의3, 제40조, 제41조, 제46조) */
    static final Set<SpecialSupplyType> CHILD_BORN_SINCE_20240619 =
            EnumSet.of(SpecialSupplyType.NEWBORN, SpecialSupplyType.MULTI_CHILD,
                    SpecialSupplyType.NEWLYWED, SpecialSupplyType.OLD_PARENTS);

    private final ReWinRestrictionProperties restriction;

    public ReWinRequirement(ReWinRestrictionProperties restriction) {
        this.restriction = restriction;
    }

    public RequirementCheck check(ApplicantProfile profile, SpecialSupplyType type) {
        String exemption = null;
        if (profile.isEverWonSpecialSupply()) {
            Optional<String> exempt = countExemption(profile, type);
            if (exempt.isEmpty()) {
                return RequirementCheck.fail(
                        "과거 특별공급 당첨 이력이 있어 특별공급은 다시 신청할 수 없습니다 (한 차례 한정, 주택공급규칙 제55조)."
                                + exceptionHint(profile, type));
            }
            exemption = exempt.get();
        }

        RequirementCheck period = periodCheck(profile);
        if (exemption != null && period.passed()) {
            return RequirementCheck.pass(exemption + " " + period.reason());
        }
        return period;
    }

    /**
     * 제55조의3 특례 — 과거 특공 당첨 이력이 있어도 이 유형은 신청할 수 있는지와 그 근거.
     * 이용자가 고른 "누가·언제"와 "2024. 6. 19. 이후 출생 자녀"만 본다(모르면 특례 없음).
     */
    static Optional<String> countExemption(ApplicantProfile profile, SpecialSupplyType type) {
        SpecialSupplyWinTiming timing = profile.getSpecialSupplyWinTiming() == null
                ? SpecialSupplyWinTiming.OTHER : profile.getSpecialSupplyWinTiming();
        if (timing == SpecialSupplyWinTiming.SPOUSE_BEFORE_MARRIAGE && SPOUSE_PRE_MARRIAGE_IGNORED.contains(type)) {
            return Optional.of("배우자의 혼인신고 전 당첨 이력은 " + type.getLabel()
                    + " 특별공급에서 따지지 않습니다 (주택공급규칙 제55조의3제1항).");
        }
        if (timing == SpecialSupplyWinTiming.SELF_BEFORE_MARRIAGE && type == SpecialSupplyType.NEWLYWED) {
            return Optional.of("혼인신고 전에 당첨된 이력이 있어도 신혼부부 특별공급은 한 차례 신청할 수 있습니다 "
                    + "(제55조의3제2항 — 이 특례로 이미 당첨된 적이 있으면 다시는 안 됩니다).");
        }
        if (profile.isHasChildBornSince20240619() && CHILD_BORN_SINCE_20240619.contains(type)) {
            return Optional.of("2024년 6월 19일 이후 출생한 자녀가 있어 특별공급 이력이 있어도 " + type.getLabel()
                    + " 특별공급을 한 차례 신청할 수 있습니다 (제55조의3제3항 — 이 특례로 이미 당첨된 적이 있으면 다시는 안 됩니다). "
                    + "본인·배우자가 주택을 소유 중이면 기존 주택 처분 조건으로 무주택 요건도 면제될 수 있으나, 이 판정에는 반영하지 않았습니다.");
        }
        return Optional.empty();
    }

    /** 특례가 있을 수 있는 유형인데 입력이 그 특례에 해당하지 않을 때 — 어떤 입력이면 달라지는지 알려 준다 */
    private static String exceptionHint(ApplicantProfile profile, SpecialSupplyType type) {
        boolean spouse = SPOUSE_PRE_MARRIAGE_IGNORED.contains(type);
        boolean self = type == SpecialSupplyType.NEWLYWED;
        boolean child = CHILD_BORN_SINCE_20240619.contains(type) && !profile.isHasChildBornSince20240619();
        java.util.List<String> cases = new java.util.ArrayList<>();
        if (spouse && self) cases.add("본인·배우자의 혼인신고 전 당첨이거나");
        else if (spouse) cases.add("배우자의 혼인신고 전 당첨이거나");
        if (child) cases.add("2024년 6월 19일 이후 출생한 자녀가 있으면");
        if (cases.isEmpty()) return "";
        String joined = String.join(" ", cases);
        if (joined.endsWith("이거나")) joined = joined.substring(0, joined.length() - "이거나".length()) + "이면";
        return " 다만 " + joined + " 신청할 수 있습니다 — 입력에서 골라 주세요.";
    }

    private RequirementCheck periodCheck(ApplicantProfile profile) {
        Integer months = profile.getMonthsSinceLastWin();
        if (months == null) {
            // pastWinInSpeculationArea 를 체크했다는 건 과거 당첨 이력이 있다는 뜻이다.
            // 그런데 경과 개월이 비어 있으면 "이력 없음"으로 단정할 근거가 없다 — 추측 대신 MISSING.
            if (profile.isPastWinInSpeculationArea()) {
                return RequirementCheck.missing("마지막 당첨일로부터 경과 개월");
            }
            return RequirementCheck.pass("재당첨 제한 기간에 걸리는 당첨 이력이 없습니다.");
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
