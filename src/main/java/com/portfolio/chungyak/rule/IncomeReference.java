package com.portfolio.chungyak.rule;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 가구 월평균소득 -> 도시근로자 월평균소득 대비 % 환산.
 *
 * 특별공급 소득 요건은 전부 이 %로 매겨진다(예: "우선공급 100% 이하, 일반 140% 이하").
 * 판정에 쓰이지만 계산은 결정론적이다 — 표를 나누는 산수뿐.
 */
@Component
public class IncomeReference {

    private final IncomeReferenceProperties properties;

    public IncomeReference(IncomeReferenceProperties properties) {
        this.properties = properties;
    }

    public String basisYear() {
        return properties.basisYear();
    }

    /**
     * @return 도시근로자 월평균소득 대비 % (반올림). 입력이 부족하거나 기준표가 없으면 null.
     */
    public Integer percentOf(Integer monthlyHouseholdIncome, Integer householdSize) {
        if (monthlyHouseholdIncome == null || householdSize == null || householdSize < 1) {
            return null;
        }
        Long base = baseFor(householdSize);
        if (base == null || base <= 0) {
            return null;
        }
        return (int) Math.round(monthlyHouseholdIncome * 100.0 / base);
    }

    /**
     * 가구원 수별 기준 소득. 표에 없는 큰 가구는 마지막 두 구간의 증가분만큼 선형 연장한다
     * (통계청 방식: 8인 초과 시 1인 증가마다 (8인-7인) 만큼 가산).
     */
    Long baseFor(int householdSize) {
        Map<Integer, Long> table = properties.monthlyAverage();
        if (table.isEmpty()) {
            return null;
        }
        Long exact = table.get(householdSize);
        if (exact != null) {
            return exact;
        }
        int maxKey = table.keySet().stream().max(Integer::compareTo).orElseThrow();
        if (householdSize < maxKey) {
            // 표 중간에 빈 칸이 있으면 가장 가까운 하위 구간을 쓴다 (보수적으로 낮은 기준)
            return table.entrySet().stream()
                    .filter(e -> e.getKey() <= householdSize)
                    .max(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .orElse(table.get(table.keySet().stream().min(Integer::compareTo).orElseThrow()));
        }
        Long top = table.get(maxKey);
        Long prev = table.get(maxKey - 1);
        long increment = (prev != null) ? top - prev : 0;
        return top + increment * (householdSize - maxKey);
    }
}
