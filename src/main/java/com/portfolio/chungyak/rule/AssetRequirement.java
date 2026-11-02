package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import org.springframework.stereotype.Component;

/**
 * 특별공급 자산 요건 확인 — 공공주택(국민) 특별공급에만 적용.
 *
 * 민영주택 특별공급은 소득 요건만 있고 자산 요건이 없으므로 그 경우 통과로 본다.
 * 공공주택이면 세대 부동산(토지+건축물) 가액 + 자동차가액 두 상한을 본다(LH 청약플러스 공공분양 자산기준,
 * 2026-11-02 확인). 총자산 기준이 아니다 — 총자산은 신혼희망타운({@code NewlywedHopeTownRule})·공공임대 쪽 기준이다.
 * 2023. 3. 28. 이후 출생 자녀가 있으면 완화 금액을 쓴다.
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
        int tier = IncomeRequirement.birthTier(profile);
        SpecialSupplyRequirementProperties.AssetAmounts amounts = limit.forBirthTier(tier);
        String relaxed = tier > 0 ? ", 2023.3.28 이후 출생 자녀 완화 기준" : "";

        if (profile.getRealEstateValue() == null) {
            return RequirementCheck.missing("세대 부동산 가액");
        }
        if (profile.getRealEstateValue() > amounts.realEstate()) {
            return RequirementCheck.fail("세대 부동산 가액 " + won(profile.getRealEstateValue())
                    + "이 공공주택 특별공급 요건(" + won(amounts.realEstate()) + " 이하" + relaxed + ")을 초과합니다 — "
                    + won(profile.getRealEstateValue() - amounts.realEstate()) + " 초과.");
        }

        if (profile.getCarValue() == null) {
            return RequirementCheck.missing("자동차가액");
        }
        if (profile.getCarValue() > amounts.carValue()) {
            return RequirementCheck.fail("자동차가액 " + won(profile.getCarValue())
                    + "이 요건(" + won(amounts.carValue()) + " 이하" + relaxed + ")을 초과합니다 — "
                    + won(profile.getCarValue() - amounts.carValue()) + " 초과.");
        }

        return RequirementCheck.pass("세대 부동산 가액·자동차가액이 공공주택 특별공급 자산 요건("
                + won(amounts.realEstate()) + "·" + won(amounts.carValue()) + " 이하" + relaxed + ")을 충족합니다.");
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
