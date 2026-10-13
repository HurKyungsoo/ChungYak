package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.SpecialSupplyType;

import java.util.Map;

/**
 * 청약홈 특별공급 신청현황 한 행 (#33) — 주택형 하나의 특공 유형별 배정 세대·신청 건수.
 * {@code getAPTSpsplyReqstStus}. 신청 건수는 해당지역·시도·기타지역을 합친 값이다.
 * 판정과 무관한 참고 정보다 — 신청이 많다고 당첨이 어렵다는 뜻은 아니다(추첨·가점·지역 우선이 다르다).
 */
public record SpecialSupplyApplication(String houseType, Map<SpecialSupplyType, Count> byType) {

    /** @param households 배정 세대 · @param applications 신청 건수 */
    public record Count(int households, int applications) {
        public Count plus(Count o) {
            return new Count(households + o.households, applications + o.applications);
        }
    }
}
