package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.RegulationFlags;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.SupplyCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
public class ApplyhomeClient implements AnnouncementSource {

    private static final String SOURCE_PREFIX = "APPLYHOME-";

    private final RestClient publicDataRestClient;
    private final PublicDataProperties properties;
    private final PublicDataParser parser;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 공고문 PDF 원문(#38) — 없으면(테스트) 원문을 가져오지 않는다 */
    private final ApplyhomeNoticeFetcher noticeFetcher;

    public ApplyhomeClient(RestClient publicDataRestClient, PublicDataProperties properties, PublicDataParser parser) {
        this(publicDataRestClient, properties, parser, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApplyhomeClient(RestClient publicDataRestClient, PublicDataProperties properties, PublicDataParser parser,
                           ApplyhomeNoticeFetcher noticeFetcher) {
        this.publicDataRestClient = publicDataRestClient;
        this.properties = properties;
        this.parser = parser;
        this.noticeFetcher = noticeFetcher;
    }

    /** 공고문 원문 — 공고 웹페이지의 PDF 첨부(#38, 공식 API 밖·끄는 스위치 있음). 판정에는 쓰지 않는다. */
    @Override
    public java.util.Optional<String> fetchNoticeContent(ExternalAnnouncement announcement) {
        if (noticeFetcher == null) return java.util.Optional.empty();
        return noticeFetcher.fetch(announcement.getNoticeUrl(), announcement.getHouseManageNo());
    }

    /** 원문이 아직 없는 기존 공고(접수 전·중)도 한 번 받는다 — 이 기능 이전에 수집된 공고용 */
    @Override
    public boolean backfillsMissingNotice() {
        return noticeFetcher != null && noticeFetcher.isEnabled();
    }

    @Override
    public String sourceName() {
        return "청약홈";
    }

    @Override
    public boolean isEnabled() {
        return true;   // 키가 없으면 각 호출이 빈 리스트를 반환하므로 항상 켜둔다
    }

    @Override
    public int maxPages() {
        return properties.getApplyhome().getMaxPages();
    }

    @Override
    public List<ExternalUnitType> fetchUnitTypes(ExternalAnnouncement announcement) {
        List<ExternalUnitType> unitTypes =
                fetchUnitTypes(announcement.getHouseManageNo(), announcement.getPblancNo());
        if (announcement.getHouseType() != HouseType.NEWLYWED_HOPE_TOWN) {
            return unitTypes;
        }
        return unitTypes.stream().map(ApplyhomeClient::reclassifyForHopeTown).toList();
    }

    /**
     * 청약홈 {@code NWWDS_HSHLDCO}(신혼부부 세대수)는 신혼희망타운 공고에서도 표준 신혼부부와
     * 같은 컬럼에 채워져 온다. 신혼희망타운은 자격기준(소득·자산·통장 요건, 혼인기간 예외)이
     * 달라 {@link SpecialSupplyType#NEWLYWED_HOPE_TOWN} 으로 옮겨 담는다 —
     * LhClient.reclassifyForHopeTown 과 같은 이유, 다만 여기는 세대수(카운트)를 그대로 옮긴다.
     */
    static ExternalUnitType reclassifyForHopeTown(ExternalUnitType unitType) {
        SupplyBreakdown breakdown = unitType.getSupplyBreakdown();
        if (breakdown == null || breakdown.countOf(SpecialSupplyType.NEWLYWED) == 0) {
            return unitType;
        }
        SupplyBreakdown moved = breakdown.toBuilder()
                .newlywed(0)
                .newlywedHopeTown(breakdown.countOf(SpecialSupplyType.NEWLYWED))
                .build();
        return unitType.toBuilder().supplyBreakdown(moved).build();
    }

    /** 공고 목록 (주택형은 아직 안 채워진 상태) */
    @Override
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

    // === 무순위·잔여세대 (getRemndrLttotPblancDetail / getRemndrLttotPblancMdl) ===
    // 같은 odcloud 서비스·같은 키. 응답 필드는 APT 와 거의 같지만 접수일이 SUBSCRPT_RCEPT_BGNDE/ENDDE 이고
    // 규제·민영/국민 구분·특공 일정이 없다(2026-10-13 라이브). ApplyhomeRemainderSource 가 수집 소스로 감싼다.

    static final String REMAINDER_PREFIX = "APPLYHOME-RM-";

    /** 무순위·잔여세대 공고 목록 page 페이지 — 최신순. 실패하면 빈 리스트. */
    public List<ExternalAnnouncement> fetchRemainderAnnouncements(int page) {
        PublicDataProperties.Applyhome config = properties.getApplyhome();
        if (config.getRemainderDetailPath() == null) return List.of();
        URI uri = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + config.getRemainderDetailPath())
                .queryParam("page", page)
                .queryParam("perPage", config.getPerPage())
                .queryParam("serviceKey", properties.getServiceKey())
                .build(true)
                .toUri();
        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseRemainderAnnouncements(body);
        } catch (Exception e) {
            log.warn("청약홈 무순위 공고 조회 실패. page={}, msg={}", page, e.getMessage());
            return List.of();
        }
    }

    /** 무순위 공고의 주택형 — 응답 필드가 APT 주택형과 같아 {@link #parseUnitTypes} 를 그대로 쓴다(특공 세대수 필드는 없어 0). */
    public List<ExternalUnitType> fetchRemainderUnitTypes(String houseManageNo, String pblancNo) {
        PublicDataProperties.Applyhome config = properties.getApplyhome();
        if (config.getRemainderModelPath() == null) return List.of();
        URI uri = UriComponentsBuilder
                .fromUriString(config.getBaseUrl() + config.getRemainderModelPath())
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
            log.warn("청약홈 무순위 주택형 조회 실패. pblancNo={}, msg={}", pblancNo, e.getMessage());
            return List.of();
        }
    }

    List<ExternalAnnouncement> parseRemainderAnnouncements(byte[] body) throws Exception {
        if (body == null || body.length == 0) return List.of();
        JsonNode data = objectMapper.readTree(body).path("data");
        if (!data.isArray()) return List.of();
        List<ExternalAnnouncement> result = new ArrayList<>();
        for (JsonNode item : data) {
            try {
                ExternalAnnouncement parsed = toRemainderExternal(item);
                if (parsed.isValid()) result.add(parsed);
            } catch (RuntimeException e) {
                log.warn("청약홈 무순위 공고 파싱 실패 — 스킵: {}", e.getMessage());
            }
        }
        return result;
    }

    private ExternalAnnouncement toRemainderExternal(JsonNode item) {
        String houseManageNo = parser.text(item, "HOUSE_MANAGE_NO");
        String pblancNo = parser.text(item, "PBLANC_NO");
        return ExternalAnnouncement.builder()
                .externalId(REMAINDER_PREFIX + houseManageNo + "-" + pblancNo)
                .houseManageNo(houseManageNo)
                .pblancNo(pblancNo)
                .houseName(parser.text(item, "HOUSE_NM"))
                .houseType(HouseType.from(parser.text(item, "HOUSE_SECD")))   // 04 무순위 · 06 불법행위 재공급
                .supplyCategory(SupplyCategory.REMAINDER)
                .houseDetailType(HouseDetailType.UNKNOWN)   // 무순위 응답엔 민영/국민 구분이 없다
                .regionName(parser.text(item, "SUBSCRPT_AREA_CODE_NM"))
                .regionCode(parser.text(item, "SUBSCRPT_AREA_CODE"))
                .address(parser.text(item, "HSSPLY_ADRES"))
                .zipCode(parser.text(item, "HSSPLY_ZIP"))
                .totalSupplyCount(parser.number(item, "TOT_SUPLY_HSHLDCO"))
                .noticeDate(parser.date(item, "RCRIT_PBLANC_DE"))
                .receptBeginDate(parser.date(item, "SUBSCRPT_RCEPT_BGNDE"))
                .receptEndDate(parser.date(item, "SUBSCRPT_RCEPT_ENDDE"))
                .winnerAnnounceDate(parser.date(item, "PRZWNER_PRESNATN_DE"))
                .regulationFlags(RegulationFlags.builder().build())   // 무순위 응답엔 규제 정보가 없다
                .noticeUrl(parser.text(item, "PBLANC_URL"))
                .homepageUrl(parser.text(item, "HMPG_ADRES"))
                .inquiryTel(parser.text(item, "MDHS_TELNO"))
                .developerName(parser.text(item, "BSNS_MBY_NM"))
                .moveInYearMonth(parser.text(item, "MVN_PREARNGE_YM"))
                .build();
    }

    /**
     * 공고의 특별공급 신청현황(주택형별 유형 배정·신청 건수) — 참고 정보(#33). 특공 접수가 끝나야 데이터가 생긴다.
     * 실패하거나 키·URL 이 없으면 빈 리스트(앱은 그대로 돈다).
     */
    public List<SpecialSupplyApplication> fetchSpecialSupplyApplications(String houseManageNo) {
        String url = properties.getApplyhome().getSpecialSupplyStatusUrl();
        if (url == null || url.isBlank() || houseManageNo == null) return List.of();

        URI uri = UriComponentsBuilder
                .fromUriString(url)
                .queryParam("page", 1)
                .queryParam("perPage", 100)
                .queryParam("cond[HOUSE_MANAGE_NO::EQ]", houseManageNo)
                .queryParam("serviceKey", properties.getServiceKey())
                .build()      // cond[..] 의 대괄호는 인코딩이 필요해 build(true) 를 쓰지 않는다 (fetchUnitTypes 와 같은 방식)
                .toUri();
        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseSpecialSupplyApplications(body);
        } catch (Exception e) {
            log.warn("청약홈 특공 신청현황 조회 실패. houseManageNo={}, msg={}", houseManageNo, e.getMessage());
            return List.of();
        }
    }

    /**
     * 특공 유형 ↔ 신청현황 필드 접미사 (CLAUDE.md "데이터에서 확인된 사실"). 배정은 {@code <접미사>_HSHLDCO},
     * 신청은 {@code CRSPAREA_/CTPRVN_/ETC_AREA_<접미사>_CNT}. 노부모만 배정 필드 이름이 다르다.
     */
    private static final Map<SpecialSupplyType, String[]> SPSPLY_FIELDS = Map.of(
            SpecialSupplyType.MULTI_CHILD, new String[]{"MNYCH_HSHLDCO", "MNYCH_CNT"},
            SpecialSupplyType.NEWLYWED, new String[]{"NWWDS_NMTW_HSHLDCO", "NWWDS_NMTW_CNT"},
            SpecialSupplyType.FIRST_TIME, new String[]{"LFE_FRST_HSHLDCO", "LFE_FRST_CNT"},
            SpecialSupplyType.OLD_PARENTS, new String[]{"OLD_PARNTS_SUPORT_HSHLDCO", "OPS_CNT"},
            SpecialSupplyType.NEWBORN, new String[]{"NWBB_NWBBSHR_HSHLDCO", "NWBB_NWBBSHR_CNT"},
            SpecialSupplyType.YOUTH, new String[]{"YGMN_HSHLDCO", "YGMN_CNT"});

    /** 파싱 실패 행은 스킵한다(외부 API 규칙). 배정·신청이 모두 0 인 유형은 담지 않는다. */
    List<SpecialSupplyApplication> parseSpecialSupplyApplications(byte[] body) throws Exception {
        if (body == null || body.length == 0) return List.of();
        JsonNode data = objectMapper.readTree(body).path("data");
        if (!data.isArray()) return List.of();

        List<SpecialSupplyApplication> out = new ArrayList<>();
        for (JsonNode row : data) {
            try {
                Map<SpecialSupplyType, SpecialSupplyApplication.Count> byType = new java.util.EnumMap<>(SpecialSupplyType.class);
                SPSPLY_FIELDS.forEach((type, f) -> {
                    int households = intOf(row, f[0]);
                    int applications = intOf(row, "CRSPAREA_" + f[1]) + intOf(row, "CTPRVN_" + f[1]) + intOf(row, "ETC_AREA_" + f[1]);
                    if (households > 0 || applications > 0) {
                        byType.put(type, new SpecialSupplyApplication.Count(households, applications));
                    }
                });
                out.add(new SpecialSupplyApplication(row.path("HOUSE_TY").asText(null), byType));
            } catch (RuntimeException e) {
                log.warn("청약홈 특공 신청현황 행 파싱 실패 — 스킵: {}", e.getMessage());
            }
        }
        return out;
    }

    /** 숫자 또는 숫자 문자열 → int. 없거나 빈 값은 0, 숫자가 아니면 예외(행 스킵). */
    private static int intOf(JsonNode row, String field) {
        JsonNode v = row.path(field);
        if (v.isMissingNode() || v.isNull()) return 0;
        if (v.isNumber()) return v.asInt();
        String s = v.asText().replace(",", "").strip();
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    // package-private: 계약 테스트(ApplyhomeParseContractTest)가 픽스처 바이트로 직접 호출한다
    List<ExternalAnnouncement> parseAnnouncements(byte[] body) throws Exception {
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

        HouseType houseType = HouseType.from(parser.text(item, "HOUSE_SECD"));
        return ExternalAnnouncement.builder()
                .externalId(SOURCE_PREFIX + houseManageNo + "-" + pblancNo)
                .houseManageNo(houseManageNo)
                .pblancNo(pblancNo)
                .houseName(parser.text(item, "HOUSE_NM"))
                .houseType(houseType)
                // APT 분양정보 API 만 수집하므로 신혼희망타운이 아니면 전부 분양이다.
                .supplyCategory(houseType == HouseType.NEWLYWED_HOPE_TOWN
                        ? SupplyCategory.HOPE_TOWN : SupplyCategory.SALE)
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

    List<ExternalUnitType> parseUnitTypes(byte[] body) throws Exception {
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
