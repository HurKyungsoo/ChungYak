package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LH 실 API 통합 확인. 기본적으로 실행하지 않는다.
 *
 * 실행: PUBLICDATA_SERVICE_KEY 를 넣고 LH_LIVE_TEST=1 로 돌린다.
 *   PUBLICDATA_SERVICE_KEY=... LH_LIVE_TEST=1 ./gradlew test --tests '*LhClientLiveTest*'
 *
 * CLAUDE.md: "바꾸기 전에 다시 호출해서 확인할 것" — 응답 구조가 바뀌면 여기서 먼저 깨진다.
 */
@EnabledIfEnvironmentVariable(named = "LH_LIVE_TEST", matches = "1")
class LhClientLiveTest {

    private LhClient client;

    @BeforeEach
    void setUp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        RestClient restClient = RestClient.builder().requestFactory(factory).build();

        PublicDataProperties props = new PublicDataProperties();
        props.setServiceKey(System.getenv("PUBLICDATA_SERVICE_KEY"));
        props.getLh().setEnabled(true);
        props.getLh().setNoticeListUrl("https://apis.data.go.kr/B552555/lhLeaseNoticeInfo1/lhLeaseNoticeInfo1");
        props.getLh().setDetailUrl("https://apis.data.go.kr/B552555/lhLeaseNoticeDtlInfo1/getLeaseNoticeDtlInfo1");
        props.getLh().setSupplyUrl("https://apis.data.go.kr/B552555/lhLeaseNoticeSplInfo1/getLeaseNoticeSplInfo1");

        client = new LhClient(restClient, props, new PublicDataParser(), new LhSpecialSupplyMapper(),
                new PdfNoticeExtractor());
    }

    @Test
    @DisplayName("목록 → 주택형까지 실제로 이어진다")
    void listThenUnitTypes() {
        List<ExternalAnnouncement> announcements = client.fetchAnnouncements(
                LocalDate.now().minusMonths(2), LocalDate.now(), 1);

        assertThat(announcements).isNotEmpty();
        ExternalAnnouncement first = announcements.stream()
                .filter(a -> "05".equals(a.getProviderParams().get("UPP_AIS_TP_CD"))
                        || "39".equals(a.getProviderParams().get("UPP_AIS_TP_CD")))
                .findFirst().orElse(announcements.get(0));

        System.out.println("공고: " + first.getHouseName() + " / " + first.getProviderParams());

        List<ExternalUnitType> unitTypes = client.fetchUnitTypes(first);
        System.out.println("주택형 " + unitTypes.size() + "개");
        unitTypes.forEach(u -> System.out.println("  " + u.getTypeName()
                + " 전용? 공급=" + u.getSupplyArea()
                + " 세대=" + u.getGeneralSupplyCount()
                + " 유형=" + presentTypes(u)));

        if (!unitTypes.isEmpty()) {
            assertThat(unitTypes.get(0).getSupplyBreakdown().isCountsKnown()).isFalse();
        }
    }

    private Set<SpecialSupplyType> presentTypes(ExternalUnitType u) {
        return u.getSupplyBreakdown().allocatedTypes().keySet();
    }

    @Test
    @DisplayName("분양 공고 — 첨부 공고문 PDF 를 내려받아 본문을 추출한다")
    void fetchesNoticeContentFromPdf() {
        List<ExternalAnnouncement> announcements = client.fetchAnnouncements(
                LocalDate.now().minusMonths(2), LocalDate.now(), 1);

        ExternalAnnouncement notice = announcements.stream()
                .filter(a -> "05".equals(a.getProviderParams().get("UPP_AIS_TP_CD")))
                .findFirst().orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(notice != null, "분양 공고가 없음");

        var content = client.fetchNoticeContent(notice);
        System.out.println("공고문 본문 " + content.map(String::length).orElse(0) + "자 — "
                + notice.getHouseName());
        content.ifPresent(t -> System.out.println("  " + t.substring(0, Math.min(300, t.length()))
                .replaceAll("\\s+", " ")));

        // PDF 본문이면 PAN_DTL_CTS(수백~1,600자)보다 훨씬 길다
        assertThat(content).isPresent();
        assertThat(content.get().length()).isGreaterThan(3000);
    }
}
