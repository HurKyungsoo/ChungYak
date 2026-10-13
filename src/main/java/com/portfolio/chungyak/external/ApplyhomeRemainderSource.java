package com.portfolio.chungyak.external;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
