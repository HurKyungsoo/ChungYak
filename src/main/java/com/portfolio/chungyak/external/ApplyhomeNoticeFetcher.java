package com.portfolio.chungyak.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 청약홈 APT 공고문(PDF) 원문 가져오기 (#38).
 *
 * <b>공식 API 밖이다.</b> 분양정보 API 에는 공고문 첨부가 없고, 공고 웹페이지({@code PBLANC_URL}) HTML 에 있는
 * PDF 링크({@code static.applyhome.co.kr/ai/aia/getAtchmnfl.do?…&atchmnflSeqNo=…})를 읽어야 한다(2026-10-13 확인).
 * 청약홈에는 이용약관·자동수집 금지 조항·robots 규칙이 없지만 회색지대라 안전장치를 건다:
 * <ul>
 *   <li>끄는 스위치 {@code publicdata.applyhome.notice-pdf-enabled}(환경변수 APPLYHOME_NOTICE_PDF_ENABLED) — 끄면 네트워크를 타지 않는다</li>
 *   <li>요청 사이 최소 간격({@code notice-request-interval-ms}), 서비스를 밝히는 User-Agent</li>
 *   <li>공고당 한 번(호출부가 원문이 없을 때만 부른다), 실패·구조 변경이면 그 공고만 건너뛴다</li>
 * </ul>
 * 화면은 원문 표 그대로·원문 링크와 함께 보여준다. 공개 전 법률 검토(#24) 대상.
 */
@Slf4j
@Component
public class ApplyhomeNoticeFetcher {

    /** 공고 페이지의 공고문 첨부 링크 — HTML 안에서는 & 가 &amp; 로 들어온다 */
    private static final Pattern PDF_LINK = Pattern.compile(
            "https://static\\.applyhome\\.co\\.kr/ai/aia/getAtchmnfl\\.do\\?[^\"'<>\\s]+");

    private final RestClient restClient;
    private final PublicDataProperties properties;
    private final PdfNoticeExtractor pdfExtractor;
    private final String userAgent;
    private long lastRequestAt;

    public ApplyhomeNoticeFetcher(RestClient publicDataRestClient, PublicDataProperties properties,
                                  PdfNoticeExtractor pdfExtractor,
                                  @Value("${app.base-url:}") String baseUrl) {
        this.restClient = publicDataRestClient;
        this.properties = properties;
        this.pdfExtractor = pdfExtractor;
        this.userAgent = "ChungyakCompass/1.0 (public notice summary; " + (baseUrl.isBlank() ? "no-url" : baseUrl) + ")";
    }

    public boolean isEnabled() {
        return properties.getApplyhome().isNoticePdfEnabled();
    }

    /** 공고 페이지 → PDF → 본문 텍스트. 꺼져 있거나 실패하면 empty. */
    public Optional<String> fetch(String noticePageUrl, String houseManageNo) {
        if (!isEnabled() || noticePageUrl == null || !noticePageUrl.startsWith("https://www.applyhome.co.kr/")) {
            return Optional.empty();
        }
        try {
            String html = new String(get(noticePageUrl), StandardCharsets.UTF_8);
            Optional<String> pdfUrl = pdfLinkIn(html);
            if (pdfUrl.isEmpty()) {
                log.info("청약홈 공고문 링크 없음 — houseManageNo={}", houseManageNo);
                return Optional.empty();
            }
            Optional<String> text = pdfExtractor.extract(get(pdfUrl.get()));
            text.ifPresent(t -> log.info("청약홈 공고문 PDF 본문 추출 — houseManageNo={}, {}자", houseManageNo, t.length()));
            return text;
        } catch (Exception e) {
            log.warn("청약홈 공고문 가져오기 실패 — houseManageNo={}, msg={}", houseManageNo, e.getMessage());
            return Optional.empty();
        }
    }

    /** 공고 페이지 HTML 에서 공고문 첨부 링크 — 첫 번째(공고문) 것 */
    static Optional<String> pdfLinkIn(String html) {
        if (html == null) return Optional.empty();
        Matcher m = PDF_LINK.matcher(html);
        return m.find() ? Optional.of(m.group().replace("&amp;", "&")) : Optional.empty();
    }

    private byte[] get(String url) throws InterruptedException {
        pace();
        return restClient.get().uri(URI.create(url)).header("User-Agent", userAgent).retrieve().body(byte[].class);
    }

    /** 요청 사이 최소 간격 — 수집 배치는 한 스레드라 단순 대기로 충분하다 */
    private synchronized void pace() throws InterruptedException {
        long wait = lastRequestAt + properties.getApplyhome().getNoticeRequestIntervalMs() - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }
}
