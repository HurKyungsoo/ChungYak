package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 전년도 도시근로자 가구원수별 월평균소득 (통계청).
 *
 * 매년 3월경 갱신된다. 코드에 박지 않고 `application.yml` 로 두어 배포 없이 바꿀 수 있게 한다.
 * `basis-year` 는 화면에 "2024년 기준" 처럼 고지하는 데 쓴다.
 *
 * 새 해 표가 나오면 지금 표를 {@code previous} 로 옮기고 새 표에 {@code applies-from}(그 표를 쓰기 시작한 공고일)을 적는다 —
 * 그 전에 나온 공고는 예전 표로 판정된다({@link YearlyFigure}).
 */
@ConfigurationProperties(prefix = "income-reference")
public record IncomeReferenceProperties(String basisYear, Map<Integer, Long> monthlyAverage,
                                        LocalDate appliesFrom, List<Version> previous) {

    @ConstructorBinding
    public IncomeReferenceProperties {
        // 조회 편의를 위해 정렬된 맵으로 보관
        monthlyAverage = monthlyAverage == null ? Map.of() : new TreeMap<>(monthlyAverage);
        previous = previous == null ? List.of() : List.copyOf(previous);
    }

    /** 버전이 하나뿐인(시작일 미확인) 표 */
    public IncomeReferenceProperties(String basisYear, Map<Integer, Long> monthlyAverage) {
        this(basisYear, monthlyAverage, null, null);
    }

    /** 예전 표 한 벌 */
    public record Version(String basisYear, LocalDate appliesFrom, Map<Integer, Long> monthlyAverage) {
        public Version {
            monthlyAverage = monthlyAverage == null ? Map.of() : new TreeMap<>(monthlyAverage);
        }
    }

    /** 현재 표 + 예전 표 → 공고일로 고를 버전 목록(설정이 어긋나면 기동 때 실패) */
    List<YearlyFigure<Map<Integer, Long>>> versions() {
        return YearlyFigure.of(new YearlyFigure<>(monthlyAverage, basisYear, appliesFrom),
                previous.stream().map(v -> new YearlyFigure<>(v.monthlyAverage(), v.basisYear(), v.appliesFrom())).toList());
    }
}
