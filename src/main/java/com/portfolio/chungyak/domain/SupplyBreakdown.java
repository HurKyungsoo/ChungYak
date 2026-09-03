package com.portfolio.chungyak.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * 특별공급 유형별 배정 세대수.
 *
 * 이 프로젝트의 핵심 데이터. 실데이터를 보면 이 값들이 "자격 유무"가 아니라
 * "배정 세대수"라, 판정 결과가 예/아니오를 넘어
 * "신혼부부 특공으로 47세대 배정 — 이 공고에서 가장 유리한 타입" 같은
 * 정량적 답변까지 낼 수 있다.
 *
 * 청년·신생아는 공공주택(HOUSE_DTL_SECD='03' + 특별법 적용)일 때만 채워진다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SupplyBreakdown {

    @Builder.Default private int multiChild = 0;
    @Builder.Default private int newlywed = 0;
    @Builder.Default private int firstTime = 0;
    @Builder.Default private int oldParents = 0;
    @Builder.Default private int institutionRecommend = 0;
    @Builder.Default private int youth = 0;
    @Builder.Default private int newborn = 0;
    @Builder.Default private int transferInstitution = 0;
    @Builder.Default private int etc = 0;

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

    /** 배정이 있는 유형만 모아서 반환 — 화면·설명 생성에 쓴다 */
    public Map<SpecialSupplyType, Integer> allocatedTypes() {
        Map<SpecialSupplyType, Integer> result = new EnumMap<>(SpecialSupplyType.class);
        for (SpecialSupplyType type : SpecialSupplyType.values()) {
            int count = countOf(type);
            if (count > 0) result.put(type, count);
        }
        return result;
    }

    public int total() {
        return multiChild + newlywed + firstTime + oldParents
                + institutionRecommend + youth + newborn + transferInstitution + etc;
    }
}
