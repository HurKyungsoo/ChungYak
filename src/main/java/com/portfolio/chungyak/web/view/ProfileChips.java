package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.web.form.EligibilityForm;

import java.util.ArrayList;
import java.util.List;

/**
 * 판정 결과 맨 위 "내 조건" 요약 칩 (목업 Verdict) — 어떤 입력으로 판정했는지 한 줄로 보여 준다.
 *
 * 입력값을 옮겨 적기만 한다. 비운 칸은 칩을 만들지 않는다(빈 값을 "0"·"없음"으로 바꿔 쓰면
 * 사용자가 입력한 것처럼 보인다). 금액은 Prices 와 같은 억·만원 표기.
 */
public final class ProfileChips {

    private ProfileChips() {}

    public static List<String> of(EligibilityForm f) {
        List<String> chips = new ArrayList<>();
        if (f.isMarried()) {
            chips.add(f.getMonthsSinceMarriage() != null ? "혼인 " + f.getMonthsSinceMarriage() + "개월" : "혼인 중");
        }
        if (f.getChildCount() > 0) chips.add("미성년 자녀 " + f.getChildCount() + "명");
        if (f.isHasNewborn()) chips.add("2세 이하 자녀");
        chips.add(f.isHouseless() ? "무주택" : "주택 소유");
        if (f.isEverOwnedHouse()) chips.add("과거 주택 소유 이력");
        if (f.getAccountMonths() != null) chips.add("통장 " + f.getAccountMonths() + "개월");
        if (f.getHouseholdSize() != null) chips.add(f.getHouseholdSize() + "인 가구");
        if (f.getMonthlyHouseholdIncome() != null) {
            String won = Prices.formatManwon(f.getMonthlyHouseholdIncome() / 10_000);
            if (won != null) chips.add("월소득 " + won + (f.isDualIncome() ? " (맞벌이)" : ""));
        }
        if (f.getTotalAssets() != null) {
            String won = Prices.formatManwon((int) Math.min(Integer.MAX_VALUE, f.getTotalAssets() / 10_000));
            if (won != null) chips.add("총자산 " + won);
        }
        if (f.isSupportingOldParents()) chips.add("노부모 부양");
        if (f.isEverWonSpecialSupply()) chips.add("특공 당첨 이력");
        return chips;
    }
}
