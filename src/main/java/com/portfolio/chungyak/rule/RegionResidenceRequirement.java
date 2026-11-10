package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.RegionNames;
import org.springframework.stereotype.Component;

/**
 * 해당 공급지역 거주요건 확인.
 *
 * 규제지역·수도권 공고는 그 지역에 일정 기간 계속 거주한 사람에게 우선/1순위 자격을 준다.
 * 미충족자도 "기타지역" 물량으로는 신청 가능하지만, 이 서비스는 그 경계를 FAIL 로 본다
 * (규제지역 통장 24개월 요건을 FAIL 로 보는 것과 같은 기준 — "우선공급 대상 여부").
 *
 * 사는 시·도({@link ApplicantProfile#getResidenceRegion()})를 알면 먼저 그걸 본다. 공고와 시·도가 다르면
 * 해당지역(시·군 이하) 거주자일 수 없으므로 거주 기간과 상관없이 FAIL 이다 — 비수도권 공고도 마찬가지
 * (비수도권에서 조례에 맡기는 것은 "얼마나 살았나"이지 "그 지역에 사나"가 아니다).
 * 시·도를 모르면 예전처럼 거주 기간만 본다. 같은 시·도여도 해당 시·군 거주까지 보장하지는 않는다.
 */
@Component
public class RegionResidenceRequirement {

    private final RegionResidenceRequirementProperties properties;

    public RegionResidenceRequirement(RegionResidenceRequirementProperties properties) {
        this.properties = properties;
    }

    public RequirementCheck check(ApplicantProfile profile, Announcement announcement) {
        String home = RegionNames.canonical(profile.getResidenceRegion());
        String target = RegionNames.canonical(announcement.getRegionName());
        if (home != null && !home.isBlank() && target != null && !target.isBlank() && !home.equals(target)) {
            return RequirementCheck.fail(home + "에 거주해 이 공고(" + target + ")의 해당지역 거주자가 아닙니다. "
                    + "해당지역 우선공급 대상이 아니며, 기타지역 순위로 신청할 수 있는지는 공고문을 확인하세요.");
        }

        boolean regulated = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isRegulatedArea();
        boolean metro = properties.isMetro(announcement.getRegionName());

        if (!regulated && !metro) {
            return RequirementCheck.pass("이 공고는 우선공급 거주요건을 별도로 확인하지 않습니다.");
        }

        Integer months = profile.getResidenceMonthsInRegion();
        if (months == null) {
            return RequirementCheck.missing("해당 공급지역 계속 거주 기간");
        }

        int required = regulated ? properties.regulatedMonths() : properties.metroMonths();
        String scope = regulated ? "투기과열지구·청약과열지역" : "수도권";

        return months >= required
                ? RequirementCheck.pass("해당 공급지역에 " + months + "개월 계속 거주해 "
                        + scope + " 거주요건(" + required + "개월)을 충족합니다.")
                : RequirementCheck.fail("해당 공급지역 거주 " + months + "개월로 " + scope
                        + " 우선공급 거주요건(" + required + "개월)에 미달합니다. "
                        + "기타지역 물량으로는 신청할 수 있습니다.",
                        ImprovementHints.residenceMonths(months, required, scope));
    }
}
