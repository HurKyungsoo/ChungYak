package com.portfolio.chungyak.external;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 청약홈 <b>무순위·잔여세대</b> 수집 소스 — APT 와 같은 서비스·같은 키라 {@link ApplyhomeClient} 의 무순위 조회를
 * 수집 소스로 감싼다. 스케줄러가 다른 소스와 똑같이 돈다.
 *
 * 무순위는 특별공급 구조가 없어 자격 판정(규칙 엔진) 대상이 아니다 — {@code Announcement.isEligibilityTarget()}.
 * 응답이 최신순이라 앞 {@code remainder-max-pages} 페이지만 받는다(2020년부터 1,700여 건이 쌓여 있다).
 */
@Component
@RequiredArgsConstructor
public class ApplyhomeRemainderSource implements AnnouncementSource {

    private final ApplyhomeClient client;
    private final PublicDataProperties properties;

    @Override
    public String sourceName() {
        return "청약홈 무순위";
    }

    @Override
    public boolean isEnabled() {
        return properties.getApplyhome().getRemainderDetailPath() != null;
    }

    @Override
    public int maxPages() {
        return properties.getApplyhome().getRemainderMaxPages();
    }

    @Override
    public List<ExternalAnnouncement> fetchAnnouncements(int page) {
        return client.fetchRemainderAnnouncements(page);
    }

    @Override
    public List<ExternalUnitType> fetchUnitTypes(ExternalAnnouncement announcement) {
        return client.fetchRemainderUnitTypes(announcement.getHouseManageNo(), announcement.getPblancNo());
    }

    /**
     * 공고문 원문 — 무순위 공고 페이지({@code selectAPTRemndrLttotPblancDetailView.do})에도 APT 와 같은 PDF 링크가 있다
     * (2026-10-15 2건 확인, 11쪽·약 1.7만 자). 상세 화면이 "신청 자격은 공고문"이라고 안내하는데 원문이 없어 Q&A 가
     * 꺼져 있었다. APT 와 같은 fetcher·안전장치(끄는 스위치·요청 간격·실패 시 스킵)를 쓴다.
     */
    @Override
    public Optional<String> fetchNoticeContent(ExternalAnnouncement announcement) {
        return client.fetchNoticeContent(announcement);
    }

    /** 이 기능 전에 수집된 접수 중 무순위 공고도 한 번 받는다 */
    @Override
    public boolean backfillsMissingNotice() {
        return client.backfillsMissingNotice();
    }
}
