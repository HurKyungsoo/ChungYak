package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.HouseDetailType;
import org.springframework.stereotype.Component;

/**
 * 청약통장 납입횟수·예치금 요건.
 *
 * 규칙이 이미 확인하는 <b>가입 기간</b>(6/24개월)과 별개다.
 *  - 국민주택(공공): 납입 <b>횟수</b> ({@code accountPaymentCount})
 *  - 민영주택: 지역별 <b>예치금</b> ({@code accountDeposit}, 전용 85㎡ 이하 기준)
 */
@Component
public class AccountRequirement {

    private final AccountRequirementProperties properties;

    public AccountRequirement(AccountRequirementProperties properties) {
        this.properties = properties;
    }

    public RequirementCheck check(ApplicantProfile profile, Announcement announcement) {
        boolean regulated = announcement.getRegulationFlags() != null
                && announcement.getRegulationFlags().isRegulatedArea();

        if (announcement.getHouseDetailType() == HouseDetailType.PUBLIC) {
            int required = properties.publicMinCount(regulated);
            Integer count = profile.getAccountPaymentCount();
            if (count == null) {
                return RequirementCheck.missing("청약통장 납입 횟수");
            }
            return count >= required
                    ? RequirementCheck.pass("청약통장 납입 " + count + "회로 국민주택 순위 요건("
                            + required + "회 이상)을 충족합니다.")
                    : RequirementCheck.fail("청약통장 납입 " + count + "회로 국민주택 순위 요건("
                            + required + "회 이상)에 미달합니다."
                            + (regulated ? " 규제지역이라 24회가 필요합니다." : ""),
                            ImprovementHints.paymentCount(count, required));
        }

        // 민영주택 — 지역별 예치금 (전용 85㎡ 이하 기준)
        int required = properties.depositFor(announcement.getRegionName());
        Integer deposit = profile.getAccountDeposit();
        if (deposit == null) {
            return RequirementCheck.missing("청약통장 예치금");
        }
        return deposit >= required
                ? RequirementCheck.pass("청약통장 예치금 " + AssetRequirement.won(deposit)
                        + "으로 이 지역 예치금 기준(전용 85㎡ 이하 " + AssetRequirement.won(required)
                        + ")을 충족합니다. 면적이 큰 주택형은 예치금이 더 필요합니다.")
                : RequirementCheck.fail("청약통장 예치금 " + AssetRequirement.won(deposit)
                        + "으로 이 지역 예치금 기준(전용 85㎡ 이하 " + AssetRequirement.won(required)
                        + ")에 미달합니다.",
                        ImprovementHints.deposit(deposit, required));
    }
}
