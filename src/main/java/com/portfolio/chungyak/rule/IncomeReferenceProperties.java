package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.TreeMap;

/**
 * 전년도 도시근로자 가구원수별 월평균소득 (통계청).
 *
 * 매년 3월경 갱신된다. 코드에 박지 않고 `application.yml` 로 두어 배포 없이 바꿀 수 있게 한다.
 * `basis-year` 는 화면에 "2024년 기준" 처럼 고지하는 데 쓴다.
 */
@ConfigurationProperties(prefix = "income-reference")
public record IncomeReferenceProperties(String basisYear, Map<Integer, Long> monthlyAverage) {

    public IncomeReferenceProperties {
        // 조회 편의를 위해 정렬된 맵으로 보관
        monthlyAverage = monthlyAverage == null ? Map.of() : new TreeMap<>(monthlyAverage);
    }
}
