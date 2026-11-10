package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.LocalDate;
import java.util.List;

/**
 * 신혼희망타운(HOUSE_SECD=10) 전용 자산기준.
 *
 * 표준 특별공급의 {@link SpecialSupplyRequirementProperties.AssetLimit} 과 다르게, 총자산과
 * 자동차가액을 따로 상한을 두지 않고 하나로 합산해 비교한다(LH 공식 산정식과 동일 —
 * 부동산+금융자산+기타자산+자동차-부채). 소득기준은 {@code SpecialSupplyType.NEWLYWED_HOPE_TOWN}
 * 키로 기존 {@code special-supply.income-limit} 표를 그대로 쓴다(구조가 같아서 별도 클래스 불필요).
 *
 * 제도 개정으로 자주 바뀐다 — 코드에 박지 않고 여기 둔다. 출처: myhome.go.kr 신혼희망타운 안내
 * (2026-09-05 확인, "2026년도 적용 기준" 3억 6,200만원). 공고 시즌마다 재확인할 것.
 */
@ConfigurationProperties(prefix = "newlywed-hope-town")
public record NewlywedHopeTownProperties(Long assetLimit, String basis, LocalDate appliesFrom,
                                         List<SpecialSupplyRequirementProperties.AmountVersion> previous) {

    @ConstructorBinding
    public NewlywedHopeTownProperties {
        previous = previous == null ? List.of() : List.copyOf(previous);
        if (assetLimit != null) versions(assetLimit, basis, appliesFrom, previous);   // 설정 검증 — 어긋나면 기동 때 실패
    }

    /** 버전 없이(시작일 미확인 한 벌) */
    public NewlywedHopeTownProperties(Long assetLimit) {
        this(assetLimit, null, null, null);
    }

    /** 공고일에 맞는 자산 상한({@link YearlyFigure}). 상한이 없으면 null */
    public YearlyFigure.Pick<Long> assetLimitFor(LocalDate noticeDate) {
        if (assetLimit == null) return null;
        return YearlyFigure.pick(versions(assetLimit, basis, appliesFrom, previous), noticeDate);
    }

    private static List<YearlyFigure<Long>> versions(Long amount, String basis, LocalDate appliesFrom,
                                                     List<SpecialSupplyRequirementProperties.AmountVersion> previous) {
        return YearlyFigure.of(new YearlyFigure<>(amount, basis, appliesFrom),
                previous.stream().map(SpecialSupplyRequirementProperties.AmountVersion::toFigure).toList());
    }
}
