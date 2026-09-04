package com.portfolio.chungyak.external;

import java.util.List;
import java.util.Optional;

/**
 * 공고 수집 소스. 청약홈(odcloud)·LH(data.go.kr)처럼 게이트웨이도 응답 구조도
 * 완전히 다른 출처를 스케줄러가 똑같이 다루도록 흡수한다.
 *
 * 구현체는 응답 차이를 {@link ExternalAnnouncement}/{@link ExternalUnitType} 로 정규화하고,
 * 실패는 예외 대신 빈 리스트로 돌려준다(해당 건만 스킵).
 */
public interface AnnouncementSource {

    /** 로그·리포트용 소스 이름 */
    String sourceName();

    /** 활용신청 미승인·키 미설정 등으로 비활성이면 스케줄러가 건너뛴다 */
    boolean isEnabled();

    /** 수집할 최대 페이지 수 */
    int maxPages();

    /** page 페이지의 공고 목록 (주택형은 아직 비어 있을 수 있다). 없으면 빈 리스트 = 수집 종료 신호 */
    List<ExternalAnnouncement> fetchAnnouncements(int page);

    /** 신규 공고의 주택형 목록 (공고당 추가 호출 1회). 소스가 제공하지 않으면 빈 리스트 */
    List<ExternalUnitType> fetchUnitTypes(ExternalAnnouncement announcement);

    /**
     * 신규 공고의 입주자모집공고문 원문(비정형 텍스트) — 벡터 검색 대상.
     * 판정에는 쓰지 않는다. 소스가 제공하지 않으면 {@code Optional.empty()}.
     */
    default Optional<String> fetchNoticeContent(ExternalAnnouncement announcement) {
        return Optional.empty();
    }
}
