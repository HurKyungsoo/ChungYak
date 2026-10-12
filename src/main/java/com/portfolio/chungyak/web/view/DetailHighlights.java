package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.UnitType;

import java.util.Comparator;
import java.util.Locale;

/**
 * 공고 상세 상단 "핵심 숫자" 벤토 카드 (#19-3).
 *
 * 긴 키-값 표에 묻혀 있던 숫자(최고 분양가·평당가·총 공급과 특공/일반 비중)를 크게 꺼낸다.
 * 값이 없는 카드는 null 로 두고 화면이 통째로 숨긴다 — "-" 를 늘어놓지 않는다.
 * 계산은 표시용 집계뿐이다(최댓값·합계·비율). 분양가는 층·향 무관 <b>최고</b> 공급금액이다(CLAUDE.md).
 *
 * @param topPrice       가장 비싼 주택형의 최고 공급금액 ("8억 6,878만원") — 없으면 null
 * @param topPriceType   그 주택형 이름
 * @param perPyeong      그 주택형의 공급면적 기준 평당가 ("2,470만원") — 면적이 없으면 null
 * @param supplyAreaNote "공급면적 116.39㎡ (35.2평)" — 평당가 근거
 * @param totalSupply    총 공급세대 — 없으면 null
 * @param specialSum     주택형 특별공급 합계 (특공 막대는 일반·특공 둘 다 0보다 클 때만)
 * @param generalSum     주택형 일반공급 합계
 */
public record DetailHighlights(
        String topPrice,
        String topPriceType,
        String perPyeong,
        String supplyAreaNote,
        Integer totalSupply,
        int specialSum,
        int generalSum) {

    private static final double M2_PER_PYEONG = 400d / 121d;

    public static DetailHighlights of(Announcement a) {
        UnitType top = a.getUnitTypes().stream()
                .filter(u -> u.getTopAmount() != null && u.getTopAmount() > 0)
                .max(Comparator.comparingInt(UnitType::getTopAmount))
                .orElse(null);

        String perPyeong = null, areaNote = null;
        if (top != null) {
            Integer pp = Prices.perPyeong(top.getTopAmount(), top.getSupplyArea());
            if (pp != null) {
                perPyeong = Prices.formatManwon(pp);
                double area = Double.parseDouble(top.getSupplyArea().trim());
                areaNote = String.format(Locale.ROOT, "공급면적 %.2f㎡ (%.1f평)", area, area / M2_PER_PYEONG);
            }
        }

        int special = a.getUnitTypes().stream().mapToInt(UnitType::getSpecialSupplyCount).sum();
        int general = a.getUnitTypes().stream().mapToInt(UnitType::getGeneralSupplyCount).sum();
        return new DetailHighlights(
                top == null ? null : Prices.formatManwon(top.getTopAmount()),
                top == null ? null : top.getTypeName(),
                perPyeong, areaNote,
                a.getTotalSupplyCount(), special, general);
    }

    /** 특공/일반 비중 막대를 그릴 수 있는지 — 둘 다 있어야 비율이 의미가 있다. */
    public boolean hasSplit() {
        return specialSum > 0 && generalSum > 0;
    }

    /** 특공 비중(%) — 막대 폭. hasSplit() 일 때만 쓴다. */
    public int specialPercent() {
        int sum = specialSum + generalSum;
        return sum == 0 ? 0 : Math.round(specialSum * 100f / sum);
    }
}
