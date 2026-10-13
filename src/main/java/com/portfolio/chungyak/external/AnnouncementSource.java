package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.Announcement;

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

    /**
     * 목록 응답에 없는 기본정보(접수 시작일·당첨자 발표일·주소·공고문 PDF 링크)를 상세 조회로 채운 사본.
     * 목록이 이미 다 주는 소스(청약홈)는 그대로 돌려준다. 실패하면 원본을 돌려준다.
     */
    default ExternalAnnouncement enrichWithDetail(ExternalAnnouncement announcement) {
        return announcement;
    }

    /**
     * 원문이 아직 없는 기존 공고(접수 전·중)의 원문을 수집 때 받아 둘지. 원문 기능이 나중에 생긴 소스용(청약홈 #38).
     * 원문이 생기면 다시 받지 않는다(원문 유무로 판단).
     */
    default boolean backfillsMissingNotice() {
        return false;
    }

    /** 이미 저장된 공고인데 상세 정보를 아직 못 채웠으면 true — 다음 수집에서 한 번 더 조회한다. */
    default boolean needsDetailBackfill(Announcement existing) {
        return false;
    }
}
