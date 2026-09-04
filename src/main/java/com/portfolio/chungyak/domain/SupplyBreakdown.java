package com.portfolio.chungyak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 특별공급 유형별 배정 세대수.
 *
 * 이 프로젝트의 핵심 데이터. 실데이터를 보면 이 값들이 "자격 유무"가 아니라
 * "배정 세대수"라, 판정 결과가 예/아니오를 넘어
 * "신혼부부 특공으로 47세대 배정 — 이 공고에서 가장 유리한 타입" 같은
 * 정량적 답변까지 낼 수 있다.
 *
 * 청년·신생아는 공공주택(HOUSE_DTL_SECD='03' + 특별법 적용)일 때만 채워진다.
 *
 * 단, LH 공고는 오픈 API 가 유형별 세대수를 주지 않는다(공고문 PDF 에만 있음).
 * 이 경우 {@link #countsKnown} 이 false 이고, 각 필드는 세대수가 아니라
 * "그 유형이 이 공고에 있는가"(1/0)만 뜻한다. 판정(자격)은 세대수를 쓰지 않으므로
 * 그대로 작동하고, 세대수를 쓰는 랭킹·표시에서만 "미상"으로 갈라진다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SupplyBreakdown {

    /**
     * 각 필드가 실제 배정 세대수인지(true, 청약홈) 유형 존재 여부만인지(false, LH).
     * 기존 데이터·청약홈 경로가 기본값이므로 true.
     */
    @Builder.Default
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean countsKnown = true;

    @Builder.Default private int multiChild = 0;
    @Builder.Default private int newlywed = 0;
    @Builder.Default private int firstTime = 0;
    @Builder.Default private int oldParents = 0;
    @Builder.Default private int institutionRecommend = 0;
    @Builder.Default private int youth = 0;
    @Builder.Default private int newborn = 0;
    @Builder.Default private int transferInstitution = 0;
    @Builder.Default private int etc = 0;

    /**
     * 세대수는 모르지만 이 유형들이 공고에 있다는 것만 아는 경우(LH).
     * 각 필드에 존재 플래그(1)를 넣고 countsKnown 을 false 로 둔다.
     */
    public static SupplyBreakdown ofPresentTypes(Set<SpecialSupplyType> present) {
        return SupplyBreakdown.builder()
                .countsKnown(false)
                .multiChild(flag(present, SpecialSupplyType.MULTI_CHILD))
                .newlywed(flag(present, SpecialSupplyType.NEWLYWED))
                .firstTime(flag(present, SpecialSupplyType.FIRST_TIME))
                .oldParents(flag(present, SpecialSupplyType.OLD_PARENTS))
                .institutionRecommend(flag(present, SpecialSupplyType.INSTITUTION_RECOMMEND))
                .youth(flag(present, SpecialSupplyType.YOUTH))
                .newborn(flag(present, SpecialSupplyType.NEWBORN))
                .transferInstitution(flag(present, SpecialSupplyType.TRANSFER_INSTITUTION))
                .etc(flag(present, SpecialSupplyType.ETC))
                .build();
    }

    private static int flag(Set<SpecialSupplyType> present, SpecialSupplyType type) {
        return present.contains(type) ? 1 : 0;
    }

    public int countOf(SpecialSupplyType type) {
        return switch (type) {
            case MULTI_CHILD -> multiChild;
            case NEWLYWED -> newlywed;
            case FIRST_TIME -> firstTime;
            case OLD_PARENTS -> oldParents;
            case INSTITUTION_RECOMMEND -> institutionRecommend;
            case YOUTH -> youth;
            case NEWBORN -> newborn;
            case TRANSFER_INSTITUTION -> transferInstitution;
            case ETC -> etc;
        };
    }

    /** 해당 유형에 배정된 세대가 하나라도 있는지 */
    public boolean hasAllocation(SpecialSupplyType type) {
        return countOf(type) > 0;
    }

    /**
     * 배정이 있는 유형만 모아서 반환 — 화면·설명 생성에 쓴다.
     * countsKnown=false 면 값은 세대수가 아니라 1(존재)이라는 점에 유의.
     */
    public Map<SpecialSupplyType, Integer> allocatedTypes() {
        Map<SpecialSupplyType, Integer> result = new EnumMap<>(SpecialSupplyType.class);
        for (SpecialSupplyType type : SpecialSupplyType.values()) {
            int count = countOf(type);
            if (count > 0) result.put(type, count);
        }
        return result;
    }

    /** 배정 세대수 합계. 세대수 미상(LH)이면 의미가 없어 0 을 돌려준다. */
    public int total() {
        if (!countsKnown) return 0;
        return multiChild + newlywed + firstTime + oldParents
                + institutionRecommend + youth + newborn + transferInstitution + etc;
    }
}
