package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import org.springframework.stereotype.Component;

/**
 * 해당 공급지역 거주요건 확인.
 *
 * 규제지역·수도권 공고는 그 지역에 일정 기간 계속 거주한 사람에게 우선/1순위 자격을 준다.
 * 미충족자도 "기타지역" 물량으로는 신청 가능하지만, 이 서비스는 그 경계를 FAIL 로 본다
 * (규제지역 통장 24개월 요건을 FAIL 로 보는 것과 같은 기준 — "우선공급 대상 여부").
 */
@Component
public class RegionResidenceRequirement {

    private final RegionResidenceRequirementProperties properties;

    public RegionResidenceRequirement(RegionResidenceRequirementProperties properties) {
        this.properties = properties;
    }

    public RequirementCheck check(ApplicantProfile profile, Announcement announcement) {
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
                        + "기타지역 물량으로는 신청할 수 있습니다.");
    }
}
