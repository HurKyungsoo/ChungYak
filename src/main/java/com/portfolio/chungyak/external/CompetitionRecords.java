package com.portfolio.chungyak.external;

/**
 * 청약홈 당첨가점·경쟁률 한 행 (#34) — {@code ApplyhomeInfoCmpetRtSvc/v1}. 참고 정보이고 판정과 무관하다.
 * 지역이 들어 있지 않아 공고(announcement)와 {@code houseManageNo} 로 이어 붙여 쓴다.
 */
public final class CompetitionRecords {

    private CompetitionRecords() {}

    /** getAPTLttotPblancScore — 주택형 × 거주구분의 최저·평균·최고 당첨가점. 0 은 추첨·미달·미집계. */
    public record Score(String houseManageNo, String modelNo, String houseType, String resideCode,
                        int lowest, int average, int highest) {}

    /** getAPTLttotPblancCmpet — 주택형 × 순위 × 거주구분의 공급·신청·경쟁률(원문: "10.17", "(△1)", "-"). */
    public record Rate(String houseManageNo, String modelNo, String houseType, int rankCode, String resideCode,
                       int supply, Integer requests, String rateText) {}
}
