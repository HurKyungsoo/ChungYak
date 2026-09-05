package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chungyak.domain.HouseDetailType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 청약홈 응답 -> 도메인 파싱 계약 테스트 (오프라인).
 *
 * 픽스처(src/test/resources/fixtures/applyhome-*.json)는 실제 응답 구조를 본뜬 것이다.
 * 파서가 읽는 필드명을 누가 바꾸면 여기서 깨진다. 실제 API 변화 감지는
 * {@code ApplyhomeApiContractTest}(라이브)가 맡는다.
 */
class ApplyhomeParseContractTest {

    /** ApplyhomeClient.toExternal 이 읽는 필드 — 이 목록이 계약이다 */
    private static final List<String> REQUIRED_DETAIL_FIELDS = List.of(
            "HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_NM", "HOUSE_SECD", "HOUSE_DTL_SECD",
            "SUBSCRPT_AREA_CODE", "SUBSCRPT_AREA_CODE_NM", "HSSPLY_ADRES", "HSSPLY_ZIP",
            "TOT_SUPLY_HSHLDCO", "RCRIT_PBLANC_DE", "RCEPT_BGNDE", "RCEPT_ENDDE",
            "SPSPLY_RCEPT_BGNDE", "SPSPLY_RCEPT_ENDDE", "PRZWNER_PRESNATN_DE",
            "SPECLT_RDN_EARTH_AT", "MDAT_TRGET_AREA_SECD", "PARCPRC_ULS_AT", "IMPRMN_BSNS_AT",
            "PUBLIC_HOUSE_EARTH_AT", "LRSCL_BLDLND_AT", "PUBLIC_HOUSE_SPCLW_APPLC_AT",
            "PBLANC_URL", "HMPG_ADRES", "MDHS_TELNO", "BSNS_MBY_NM", "CNSTRCT_ENTRPS_NM",
            "MVN_PREARNGE_YM");

    private static final List<String> REQUIRED_MDL_FIELDS = List.of(
            "MODEL_NO", "HOUSE_TY", "SUPLY_AR", "SUPLY_HSHLDCO", "SPSPLY_HSHLDCO",
            "MNYCH_HSHLDCO", "NWWDS_HSHLDCO", "LFE_FRST_HSHLDCO", "OLD_PARNTS_SUPORT_HSHLDCO",
            "INSTT_RECOMEND_HSHLDCO", "YGMN_HSHLDCO", "NWBB_HSHLDCO",
            "TRANSR_INSTT_ENFSN_HSHLDCO", "ETC_HSHLDCO", "LTTOT_TOP_AMOUNT");

    private final ApplyhomeClient client = new ApplyhomeClient(null, null, new PublicDataParser());
    private final ObjectMapper mapper = new ObjectMapper();

    private byte[] fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("공고 목록 응답이 도메인 필드로 정확히 매핑된다")
    void parsesDetail() throws Exception {
        List<ExternalAnnouncement> list = client.parseAnnouncements(fixture("applyhome-detail.json"));

        assertThat(list).hasSize(2);

        ExternalAnnouncement a = list.get(0);
        assertThat(a.getHouseName()).isEqualTo("올 뉴 챔피언스시티 1차");
        assertThat(a.getPblancNo()).isEqualTo("2026000419");
        assertThat(a.getHouseDetailType()).isEqualTo(HouseDetailType.PRIVATE);
        assertThat(a.getRegionName()).isEqualTo("광주");
        assertThat(a.getTotalSupplyCount()).isEqualTo(3216);
        assertThat(a.getReceptEndDate()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(a.getWinnerAnnounceDate()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(a.getRegulationFlags().isRegulatedArea()).isFalse();
        assertThat(a.isValid()).isTrue();

        ExternalAnnouncement b = list.get(1);
        assertThat(b.getHouseDetailType()).isEqualTo(HouseDetailType.PUBLIC);
        assertThat(b.getRegulationFlags().isRegulatedArea()).isTrue();   // 투기과열 Y
        assertThat(b.getSpecialReceptBeginDate()).isNull();              // null 허용 필드
    }

    @Test
    @DisplayName("주택형 응답의 특별공급 세부배정이 정확히 매핑된다")
    void parsesMdl() throws Exception {
        List<ExternalUnitType> list = client.parseUnitTypes(fixture("applyhome-mdl.json"));

        assertThat(list).hasSize(2);
        ExternalUnitType u = list.get(0);
        assertThat(u.getModelNo()).isEqualTo("01");
        assertThat(u.getTypeName()).isEqualTo("084.9730A");
        assertThat(u.getGeneralSupplyCount()).isEqualTo(141);
        assertThat(u.getSpecialSupplyCount()).isEqualTo(172);
        assertThat(u.getSupplyBreakdown().getNewlywed()).isEqualTo(47);
        assertThat(u.getSupplyBreakdown().getMultiChild()).isEqualTo(31);
        assertThat(u.getSupplyBreakdown().getNewborn()).isEqualTo(31);
        assertThat(u.getTopAmount()).isEqualTo(87000);   // 문자열 "87000" -> 87000
        // 세부배정 합 == 특공 세대수
        assertThat(u.getSupplyBreakdown().total()).isEqualTo(u.getSpecialSupplyCount());
    }

    @Test
    @DisplayName("reclassifyForHopeTown — 신혼희망타운 공고면 NWWDS_HSHLDCO(신혼) 세대수를 " +
            "NEWLYWED_HOPE_TOWN 컬럼으로 옮긴다 (표준 신혼부부와 자격기준이 다르므로)")
    void reclassifiesNewlywedCountForHopeTown() throws Exception {
        ExternalUnitType original = client.parseUnitTypes(fixture("applyhome-mdl.json")).get(0);
        assertThat(original.getSupplyBreakdown().getNewlywed()).isEqualTo(47);

        ExternalUnitType moved = ApplyhomeClient.reclassifyForHopeTown(original);

        assertThat(moved.getSupplyBreakdown().getNewlywed()).isZero();
        assertThat(moved.getSupplyBreakdown().getNewlywedHopeTown()).isEqualTo(47);
        // 다른 유형·필드는 그대로
        assertThat(moved.getSupplyBreakdown().getMultiChild()).isEqualTo(original.getSupplyBreakdown().getMultiChild());
        assertThat(moved.getTypeName()).isEqualTo(original.getTypeName());

        // 신혼 배정이 없으면 신혼희망타운이라도 바꿀 게 없다
        ExternalUnitType noNewlywed = original.toBuilder()
                .supplyBreakdown(original.getSupplyBreakdown().toBuilder().newlywed(0).build())
                .build();
        assertThat(ApplyhomeClient.reclassifyForHopeTown(noNewlywed)).isEqualTo(noNewlywed);
    }

    @Test
    @DisplayName("픽스처가 파서 계약(읽는 필드)을 모두 담고 있다 — 픽스처를 함부로 줄이지 못하게")
    void fixtureCoversContract() throws Exception {
        JsonNode detail = mapper.readTree(fixture("applyhome-detail.json")).path("data").get(0);
        for (String f : REQUIRED_DETAIL_FIELDS) {
            assertThat(detail.has(f)).as("detail 픽스처에 필드 %s 가 없다", f).isTrue();
        }
        JsonNode mdl = mapper.readTree(fixture("applyhome-mdl.json")).path("data").get(0);
        for (String f : REQUIRED_MDL_FIELDS) {
            assertThat(mdl.has(f)).as("mdl 픽스처에 필드 %s 가 없다", f).isTrue();
        }
    }
}
