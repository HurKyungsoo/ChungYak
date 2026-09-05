package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import org.springframework.stereotype.Component;

/**
 * 특별공급 자산 요건 확인 — 공공주택(국민) 특별공급에만 적용.
 *
 * 민영주택 특별공급은 소득 요건만 있고 자산 요건이 없으므로 그 경우 통과로 본다.
 * 공공주택이면 총자산 + 자동차가액 두 상한을 본다.
 */
@Component
public class AssetRequirement {

    private final SpecialSupplyRequirementProperties requirements;

    public AssetRequirement(SpecialSupplyRequirementProperties requirements) {
        this.requirements = requirements;
    }

    public RequirementCheck check(ApplicantProfile profile, Announcement announcement) {
        if (announcement.getHouseDetailType() != HouseDetailType.PUBLIC) {
            return RequirementCheck.pass("민영주택은 특별공급 자산 요건이 없습니다.");
        }

        SpecialSupplyRequirementProperties.AssetLimit limit = requirements.assetLimit();
        if (limit == null) {
            return RequirementCheck.pass("자산 상한이 설정돼 있지 않습니다.");
        }

        if (profile.getTotalAssets() == null) {
            return RequirementCheck.missing("총자산");
        }
        if (profile.getTotalAssets() > limit.totalAssets()) {
            return RequirementCheck.fail("총자산 " + won(profile.getTotalAssets())
                    + "이 공공주택 특별공급 요건(" + won(limit.totalAssets()) + " 이하)을 초과합니다.");
        }

        if (profile.getCarValue() == null) {
            return RequirementCheck.missing("자동차가액");
        }
        if (profile.getCarValue() > limit.carValue()) {
            return RequirementCheck.fail("자동차가액 " + won(profile.getCarValue())
                    + "이 요건(" + won(limit.carValue()) + " 이하)을 초과합니다.");
        }

        return RequirementCheck.pass("총자산·자동차가액이 공공주택 특별공급 자산 요건을 충족합니다.");
    }

    /** 원 → "3억 7,900만원" 같은 읽기 쉬운 문자열 */
    public static String won(long amount) {
        long eok = amount / 100_000_000;
        long man = (amount % 100_000_000) / 10_000;
        StringBuilder sb = new StringBuilder();
        if (eok > 0) sb.append(eok).append("억");
        if (man > 0) sb.append(sb.isEmpty() ? "" : " ").append(String.format("%,d", man)).append("만");
        if (sb.isEmpty()) sb.append(String.format("%,d", amount)).append("원");
        else sb.append("원");
        return sb.toString();
    }
}
