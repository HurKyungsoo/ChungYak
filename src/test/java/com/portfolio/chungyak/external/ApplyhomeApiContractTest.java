package com.portfolio.chungyak.external;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청약홈 API 계약 라이브 테스트.
 *
 * PUBLICDATA_SERVICE_KEY 가 있을 때만 돈다. 실제 응답을 받아 파서가 핵심 필드를
 * 여전히 채우는지 확인한다 — API 가 필드를 바꾸면 파서는 조용히 null 을 넣으므로,
 * 여기서 잡지 않으면 수집이 며칠씩 반쪽짜리로 돈다.
 * (전체 수집 실패는 SyncHealthIndicator 가 잡지만, 그건 "0건"만 감지한다.)
 */
@EnabledIfEnvironmentVariable(named = "PUBLICDATA_SERVICE_KEY", matches = ".+")
class ApplyhomeApiContractTest {

    private ApplyhomeClient client;

    @BeforeEach
    void setUp() {
        PublicDataProperties props = new PublicDataProperties();
        props.setServiceKey(System.getenv("PUBLICDATA_SERVICE_KEY"));
        PublicDataProperties.Applyhome cfg = props.getApplyhome();
        cfg.setBaseUrl("https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1");
        cfg.setDetailPath("/getAPTLttotPblancDetail");
        cfg.setModelPath("/getAPTLttotPblancMdl");
        cfg.setPerPage(10);
        client = new ApplyhomeClient(RestClient.create(), props, new PublicDataParser());
    }

    @Test
    @DisplayName("공고 목록 응답이 여전히 핵심 필드를 채운다")
    void detailContract() {
        List<ExternalAnnouncement> list = client.fetchAnnouncements(1);

        assertThat(list)
                .as("공고 목록이 비었다 — API 다운/키 만료/스키마 변경 의심")
                .isNotEmpty();

        ExternalAnnouncement a = list.get(0);
        assertThat(a.getHouseName()).as("HOUSE_NM").isNotBlank();
        assertThat(a.getHouseManageNo()).as("HOUSE_MANAGE_NO").isNotBlank();
        assertThat(a.getPblancNo()).as("PBLANC_NO").isNotBlank();
        assertThat(a.getRegionName()).as("SUBSCRPT_AREA_CODE_NM").isNotBlank();
        assertThat(a.getReceptEndDate()).as("RCEPT_ENDDE").isNotNull();
        assertThat(a.getHouseType()).as("HOUSE_SECD").isNotNull();
        assertThat(a.getHouseDetailType()).as("HOUSE_DTL_SECD").isNotNull();
        assertThat(a.getRegulationFlags()).as("규제 플래그").isNotNull();
        // 목록 대부분이 유효 판정을 받아야 한다 (일부만 스킵되는 건 정상)
        long valid = list.stream().filter(ExternalAnnouncement::isValid).count();
        assertThat(valid).as("유효 공고 비율").isGreaterThanOrEqualTo(list.size() - 1L);
    }

    @Test
    @DisplayName("주택형 응답이 여전히 특별공급 세부배정을 채운다")
    void mdlContract() {
        // 특공 물량이 있는 공고를 찾는다 (앞쪽 몇 페이지)
        ExternalUnitType found = null;
        for (int page = 1; page <= 3 && found == null; page++) {
            for (ExternalAnnouncement a : client.fetchAnnouncements(page)) {
                List<ExternalUnitType> units = client.fetchUnitTypes(a.getHouseManageNo(), a.getPblancNo());
                found = units.stream()
                        .filter(u -> u.getSpecialSupplyCount() > 0)
                        .findFirst().orElse(null);
                if (found != null) break;
            }
        }

        assertThat(found).as("특공 물량이 있는 주택형을 못 찾음 — Mdl 응답 스키마 변경 의심").isNotNull();
        assertThat(found.getTypeName()).as("HOUSE_TY").isNotBlank();
        assertThat(found.getModelNo()).as("MODEL_NO").isNotBlank();
        assertThat(found.getSupplyBreakdown()).as("세부배정").isNotNull();
        // 세부배정 합이 특공 세대수와 얼추 맞아야 한다 (신혼희망타운 등은 0 이므로 <= 로만)
        assertThat(found.getSupplyBreakdown().total())
                .as("세부배정 합이 특공 세대수를 넘으면 매핑 오류")
                .isLessThanOrEqualTo(found.getSpecialSupplyCount());
    }
}
