package com.portfolio.chungyak.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 청약홈 공고문 PDF 가져오기 (#38) — 네트워크 없이 링크 추출·스위치만 검증. */
class ApplyhomeNoticeFetcherTest {

    @Test
    @DisplayName("공고 페이지 HTML 에서 공고문 첨부 링크를 찾는다 — &amp; 는 & 로 (2026-10-13 실제 페이지 구조)")
    void findsPdfLink() {
        String html = "<a href=\"https://static.applyhome.co.kr/ai/aia/getAtchmnfl.do?houseManageNo=2026000492&amp;pblancNo=2026000492"
                + "&amp;atchmnflSeqNo=1995082&amp;atchmnflSn=5\" class=\"radius_btn\">모집공고문 보기</a>";

        assertThat(ApplyhomeNoticeFetcher.pdfLinkIn(html)).contains(
                "https://static.applyhome.co.kr/ai/aia/getAtchmnfl.do?houseManageNo=2026000492&pblancNo=2026000492"
                        + "&atchmnflSeqNo=1995082&atchmnflSn=5");
        assertThat(ApplyhomeNoticeFetcher.pdfLinkIn("<html>첨부 없음</html>")).isEmpty();
    }

    @Test
    @DisplayName("스위치를 끄면 네트워크를 타지 않는다 — 청약홈 웹페이지가 아닌 주소도 부르지 않는다")
    void disabledOrForeignUrlDoesNothing() {
        PublicDataProperties props = new PublicDataProperties();
        props.getApplyhome().setNoticePdfEnabled(false);
        // RestClient 가 null 이라 호출하면 NPE — 비어 있게 돌아오면 네트워크를 안 탄 것이다
        ApplyhomeNoticeFetcher off = new ApplyhomeNoticeFetcher(null, props, new PdfNoticeExtractor(), "");
        assertThat(off.fetch("https://www.applyhome.co.kr/ai/aia/x.do", "1")).isEmpty();

        props.getApplyhome().setNoticePdfEnabled(true);
        ApplyhomeNoticeFetcher on = new ApplyhomeNoticeFetcher(null, props, new PdfNoticeExtractor(), "");
        assertThat(on.fetch("https://evil.example/x", "1")).isEmpty();
        assertThat(on.fetch(null, "1")).isEmpty();
    }
}
