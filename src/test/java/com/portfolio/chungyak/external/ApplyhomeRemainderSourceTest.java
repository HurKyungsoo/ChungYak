package com.portfolio.chungyak.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplyhomeRemainderSourceTest {

    @Test
    @DisplayName("무순위 공고도 공고문 원문을 받는다 — APT 와 같은 fetcher, 접수 중 공고 백필 포함")
    void fetchesNoticeContentLikeApt() {
        ApplyhomeClient client = mock(ApplyhomeClient.class);
        ExternalAnnouncement a = ExternalAnnouncement.builder()
                .noticeUrl("https://www.applyhome.co.kr/ai/aia/selectAPTRemndrLttotPblancDetailView.do?houseManageNo=1&pblancNo=1")
                .houseManageNo("1").build();
        when(client.fetchNoticeContent(a)).thenReturn(Optional.of("무순위 공고문"));
        when(client.backfillsMissingNotice()).thenReturn(true);

        ApplyhomeRemainderSource source = new ApplyhomeRemainderSource(client, new PublicDataProperties());

        assertThat(source.fetchNoticeContent(a)).hasValue("무순위 공고문");
        assertThat(source.backfillsMissingNotice()).isTrue();
    }

    @Test
    @DisplayName("무순위 페이지의 PDF 링크도 APT 와 같은 모양으로 찾는다 (2026-10-15 실제 페이지 링크)")
    void remainderPageLink() {
        String html = "<a href=\"https://static.applyhome.co.kr/ai/aia/getAtchmnfl.do?houseManageNo=2026910262&amp;"
                + "pblancNo=2026910262&amp;atchmnflSeqNo=1998997&amp;atchmnflSn=3\">모집공고문</a>";
        assertThat(ApplyhomeNoticeFetcher.pdfLinkIn(html)).hasValue("https://static.applyhome.co.kr/ai/aia/getAtchmnfl.do?"
                + "houseManageNo=2026910262&pblancNo=2026910262&atchmnflSeqNo=1998997&atchmnflSn=3");
    }
}
