package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.UnitType;

import java.util.Comparator;
import java.util.List;
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
        int generalSum,
        List<TypeTotal> typeTotals,
        List<String> presentTypesWithoutCounts,
        List<String> absentCommonTypes) {

    /**
     * 특별공급 유형 하나의 공고 전체 배정(주택형 합계) — "특별공급 유형별 배정" 카드 (목업 Main).
     * {@code type} 은 enum 이름 — 화면이 "내 판정 요약" 결과로 타일에 자격 표시를 칠할 때 쓴다.
     */
    public record TypeTotal(String type, String label, int count, int sharePercent, String note) {}

    /**
     * 일반인이 직접 신청하는 유형이 아니라 같은 무게로 보이면 오해할 수 있는 유형 — 타일에 짧게 표시한다.
     * (기관추천은 국가유공자·장애인 등 해당 기관의 추천이 있어야, 이전기관은 이전 대상 기관 종사자만 신청한다)
     */
    private static final java.util.Map<SpecialSupplyType, String> TYPE_NOTES = java.util.Map.of(
            SpecialSupplyType.INSTITUTION_RECOMMEND, "기관 추천 필요",
            SpecialSupplyType.TRANSFER_INSTITUTION, "이전기관 종사자만");

    /**
     * "이 공고에 없는 유형" 한 줄에 올릴 후보 — 일반인이 직접 신청하는 유형만.
     * 기관추천·이전기관은 추천·이전 대상자만, 기타는 분류 밖, 신혼희망타운은 별도 공고라 빠져 있어도 정상이다.
     */
    private static final List<SpecialSupplyType> COMMON_TYPES = List.of(
            SpecialSupplyType.NEWLYWED, SpecialSupplyType.FIRST_TIME, SpecialSupplyType.MULTI_CHILD,
            SpecialSupplyType.OLD_PARENTS, SpecialSupplyType.NEWBORN, SpecialSupplyType.YOUTH);

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

        // 배정이 있는 유형만, 많은 순. 세대수를 모르는 주택형(LH, countsKnown=false)은 countOf 가 세대수가 아니라
        // "유형 있음" 표시(1)라 더하면 1세대로 보인다 — 세대수를 아는 주택형만 센다.
        java.util.Map<SpecialSupplyType, Integer> counts = new java.util.LinkedHashMap<>();
        for (SpecialSupplyType t : a.visibleSupplyTypes()) {
            int c = a.getUnitTypes().stream()
                    .filter(u -> u.getSupplyBreakdown() != null && u.getSupplyBreakdown().isCountsKnown())
                    .mapToInt(u -> u.getSupplyBreakdown().countOf(t)).sum();
            if (c > 0) counts.put(t, c);
        }
        // 비율의 분모는 타일 숫자의 합 — 원본의 "특공 합계"(specialSum)와 다를 수 있어, 타일끼리 더하면 100% 가 되게 한다.
        // 비율은 "이 공고 특공 중 이 유형의 몫"일 뿐이다 — 세대가 많다고 당첨이 쉽다는 뜻이 아니다(경쟁률 데이터 없음).
        int typeSum = counts.values().stream().mapToInt(Integer::intValue).sum();
        // 많은 순(목업 Main) — 한 공고 안에서 어느 유형이 큰지가 먼저 보이게. 같으면 enum 선언 순서.
        List<TypeTotal> totals = counts.entrySet().stream()
                .map(e -> new TypeTotal(e.getKey().name(), e.getKey().getLabel(), e.getValue(),
                        Math.round(e.getValue() * 100f / typeSum), TYPE_NOTES.get(e.getKey())))
                .sorted(Comparator.comparingInt(TypeTotal::count).reversed())
                .toList();

        // 세대수를 모르는 주택형(LH)에서 "있다"고만 표시된 유형 — 카드를 숨기지 않고 유형 이름만 보여 준다.
        List<String> presentWithoutCounts = java.util.Arrays.stream(SpecialSupplyType.values())
                .filter(t -> a.getUnitTypes().stream().anyMatch(u -> u.getSupplyBreakdown() != null
                        && !u.getSupplyBreakdown().isCountsKnown() && u.getSupplyBreakdown().countOf(t) > 0))
                .map(SpecialSupplyType::getLabel)
                .toList();

        // 세대수를 아는 공고에서만 "없다"고 말한다 — 모르는 공고에서 0 은 "없음"이 아니라 "모름"이다.
        List<String> absent = totals.isEmpty() ? List.of() : COMMON_TYPES.stream()
                .filter(t -> totals.stream().noneMatch(tt -> tt.type().equals(t.name())))
                .map(SpecialSupplyType::getLabel)
                .toList();

        return new DetailHighlights(
                top == null ? null : Prices.formatManwon(top.getTopAmount()),
                top == null ? null : top.getTypeName(),
                perPyeong, areaNote,
                a.getTotalSupplyCount(), special, general, totals, presentWithoutCounts, absent);
    }

    /**
     * 특별공급은 있는데 유형별 세대수를 모르는 공고인지 — 청약홈 신혼희망타운(총량만 온다)이나 LH(유형만 온다).
     * 이때 카드를 숨기면 "특공이 없는" 공고처럼 보인다 — CLAUDE.md: 세지 못했다고 밝히고 공고문으로 넘긴다.
     */
    public boolean typeCountsUnknown() {
        return typeTotals.isEmpty() && (specialSum > 0 || !presentTypesWithoutCounts.isEmpty());
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
