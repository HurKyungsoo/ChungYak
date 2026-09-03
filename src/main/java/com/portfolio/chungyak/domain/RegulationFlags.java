package com.portfolio.chungyak.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * 규제 관련 Y/N 플래그 묶음.
 *
 * 청약홈 응답에 Y/N 문자열로 오는 필드가 7개나 되는데, 개별 컬럼으로 흩어두면
 * 엔티티가 지저분해지고 규칙 엔진에서도 하나씩 꺼내 써야 한다.
 * 값객체로 묶어 "이 공고가 어떤 규제를 받는가"를 한 덩어리로 다룬다.
 *
 * 규제 여부는 청약 자격 판정에 직접 영향을 준다 —
 * 투기과열지구/조정대상지역은 청약통장 가입기간·무주택기간 요건이 더 엄격하다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RegulationFlags {

    /** 투기과열지구 (SPECLT_RDN_EARTH_AT) */
    private boolean speculationOverheated;

    /** 조정대상지역 (MDAT_TRGET_AREA_SECD) */
    private boolean adjustmentTarget;

    /** 분양가상한제 (PARCPRC_ULS_AT) */
    private boolean priceCapApplied;

    /** 정비사업 (IMPRMN_BSNS_AT) */
    private boolean redevelopment;

    /** 공공주택지구 (PUBLIC_HOUSE_EARTH_AT) */
    private boolean publicHousingDistrict;

    /** 대규모 택지개발지구 (LRSCL_BLDLND_AT) */
    private boolean largeScaleDevelopment;

    /** 공공주택 특별법 적용 (PUBLIC_HOUSE_SPCLW_APPLC_AT) */
    private boolean publicHousingSpecialLaw;

    /** 규제지역인지 — 투기과열 또는 조정대상 */
    public boolean isRegulatedArea() {
        return speculationOverheated || adjustmentTarget;
    }
}
