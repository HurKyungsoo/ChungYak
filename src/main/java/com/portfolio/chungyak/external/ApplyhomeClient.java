package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국부동산원 청약홈 클라이언트 (odcloud 게이트웨이).
 *
 * 다른 공공데이터 API 와 다른 점:
 *  - 호스트가 api.odcloud.kr (api.data.go.kr 아님)
 *  - 응답 래퍼가 {data:[...], totalCount, matchCount, page, perPage}
 *  - 조건 검색이 cond[FIELD::EQ]=value 문법
 *  - 페이징이 page/perPage (numOfRows/pageNo 아님)
 *
 * 필드명은 Swagger 명세로 확정했고 라이브 호출로 검증했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyhomeClient {

    private static final String SOURCE_PREFIX = "APPLYHOME-";

    private final RestClient publicDataRestClient;
    private final PublicDataProperties properties;
    private final PublicDataParser parser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 공고 목록 (주택형은 아직 안 채워진 상태) */
    public List<ExternalAnnouncement> fetchAnnouncements(int page) {
        PublicDataProperties.Applyhome config = properties.getApplyhome();

        URI uri = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + config.getDetailPath())
                .queryParam("page", page)
                .queryParam("perPage", config.getPerPage())
                .queryParam("serviceKey", properties.getServiceKey())
                .build(true)
                .toUri();

        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseAnnouncements(body);
        } catch (Exception e) {
            log.warn("청약홈 공고 조회 실패. page={}, msg={}", page, e.getMessage());
            return List.of();
        }
    }

    /** 특정 공고의 주택형 목록 */
    public List<ExternalUnitType> fetchUnitTypes(String houseManageNo, String pblancNo) {
        PublicDataProperties.Applyhome config = properties.getApplyhome();

        URI uri = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + config.getModelPath())
                .queryParam("page", 1)
                .queryParam("perPage", 100)
                .queryParam("cond[HOUSE_MANAGE_NO::EQ]", houseManageNo)
                .queryParam("cond[PBLANC_NO::EQ]", pblancNo)
                .queryParam("serviceKey", properties.getServiceKey())
                .build()      // cond[..] 의 대괄호는 인코딩이 필요해 build(true) 를 쓰지 않는다
                .toUri();

        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseUnitTypes(body);
        } catch (Exception e) {
            log.warn("청약홈 주택형 조회 실패. pblancNo={}, msg={}", pblancNo, e.getMessage());
            return List.of();
        }
    }

    private List<ExternalAnnouncement> parseAnnouncements(byte[] body) throws Exception {
        if (body == null || body.length == 0) return List.of();

        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            log.warn("청약홈 응답에 data 배열이 없음: {}", root.toString().substring(0, Math.min(300, root.toString().length())));
            return List.of();
        }

        List<ExternalAnnouncement> result = new ArrayList<>();
        for (JsonNode item : data) {
            ExternalAnnouncement parsed = toExternal(item);
            if (parsed.isValid()) {
                result.add(parsed);
            }
        }
        return result;
    }

    private ExternalAnnouncement toExternal(JsonNode item) {
        String houseManageNo = parser.text(item, "HOUSE_MANAGE_NO");
        String pblancNo = parser.text(item, "PBLANC_NO");

        RegulationFlags flags = RegulationFlags.builder()
                .speculationOverheated(parser.flag(item, "SPECLT_RDN_EARTH_AT"))
                .adjustmentTarget(parser.flag(item, "MDAT_TRGET_AREA_SECD"))
                .priceCapApplied(parser.flag(item, "PARCPRC_ULS_AT"))
                .redevelopment(parser.flag(item, "IMPRMN_BSNS_AT"))
                .publicHousingDistrict(parser.flag(item, "PUBLIC_HOUSE_EARTH_AT"))
                .largeScaleDevelopment(parser.flag(item, "LRSCL_BLDLND_AT"))
                .publicHousingSpecialLaw(parser.flag(item, "PUBLIC_HOUSE_SPCLW_APPLC_AT"))
                .build();

        return ExternalAnnouncement.builder()
                .externalId(SOURCE_PREFIX + houseManageNo + "-" + pblancNo)
                .houseManageNo(houseManageNo)
                .pblancNo(pblancNo)
                .houseName(parser.text(item, "HOUSE_NM"))
                .houseType(HouseType.from(parser.text(item, "HOUSE_SECD")))
                .houseDetailType(HouseDetailType.from(parser.text(item, "HOUSE_DTL_SECD")))
                .regionName(parser.text(item, "SUBSCRPT_AREA_CODE_NM"))
                .regionCode(parser.text(item, "SUBSCRPT_AREA_CODE"))
                .address(parser.text(item, "HSSPLY_ADRES"))
                .zipCode(parser.text(item, "HSSPLY_ZIP"))
                .totalSupplyCount(parser.number(item, "TOT_SUPLY_HSHLDCO"))
                .noticeDate(parser.date(item, "RCRIT_PBLANC_DE"))
                .receptBeginDate(parser.date(item, "RCEPT_BGNDE"))
                .receptEndDate(parser.date(item, "RCEPT_ENDDE"))
                .specialReceptBeginDate(parser.date(item, "SPSPLY_RCEPT_BGNDE"))
                .specialReceptEndDate(parser.date(item, "SPSPLY_RCEPT_ENDDE"))
                .winnerAnnounceDate(parser.date(item, "PRZWNER_PRESNATN_DE"))
                .regulationFlags(flags)
                .noticeUrl(parser.text(item, "PBLANC_URL"))
                .homepageUrl(parser.text(item, "HMPG_ADRES"))
                .inquiryTel(parser.text(item, "MDHS_TELNO"))
                .developerName(parser.text(item, "BSNS_MBY_NM"))
                .constructorName(parser.text(item, "CNSTRCT_ENTRPS_NM"))
                .moveInYearMonth(parser.text(item, "MVN_PREARNGE_YM"))
                .build();
    }

    private List<ExternalUnitType> parseUnitTypes(byte[] body) throws Exception {
        if (body == null || body.length == 0) return List.of();

        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        if (!data.isArray()) return List.of();

        List<ExternalUnitType> result = new ArrayList<>();
        for (JsonNode item : data) {
            SupplyBreakdown breakdown = SupplyBreakdown.builder()
                    .multiChild(parser.intOrZero(item, "MNYCH_HSHLDCO"))
                    .newlywed(parser.intOrZero(item, "NWWDS_HSHLDCO"))
                    .firstTime(parser.intOrZero(item, "LFE_FRST_HSHLDCO"))
                    .oldParents(parser.intOrZero(item, "OLD_PARNTS_SUPORT_HSHLDCO"))
                    .institutionRecommend(parser.intOrZero(item, "INSTT_RECOMEND_HSHLDCO"))
                    .youth(parser.intOrZero(item, "YGMN_HSHLDCO"))
                    .newborn(parser.intOrZero(item, "NWBB_HSHLDCO"))
                    .transferInstitution(parser.intOrZero(item, "TRANSR_INSTT_ENFSN_HSHLDCO"))
                    .etc(parser.intOrZero(item, "ETC_HSHLDCO"))
                    .build();

            result.add(ExternalUnitType.builder()
                    .modelNo(parser.text(item, "MODEL_NO"))
                    .typeName(parser.text(item, "HOUSE_TY"))
                    .supplyArea(parser.text(item, "SUPLY_AR"))
                    .generalSupplyCount(parser.intOrZero(item, "SUPLY_HSHLDCO"))
                    .specialSupplyCount(parser.intOrZero(item, "SPSPLY_HSHLDCO"))
                    .supplyBreakdown(breakdown)
                    .topAmount(parser.number(item, "LTTOT_TOP_AMOUNT"))
                    .build());
        }
        return result;
    }
}
