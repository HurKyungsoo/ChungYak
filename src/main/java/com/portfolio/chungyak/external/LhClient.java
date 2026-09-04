package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chungyak.domain.HouseDetailType;
import com.portfolio.chungyak.domain.HouseType;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 한국토지주택공사(LH) 분양·임대 공고 클라이언트 — data.go.kr B552555 게이트웨이.
 *
 * 청약홈(odcloud)과 다른 점:
 *  - 호스트가 apis.data.go.kr, 서비스 그룹이 /B552555/...
 *  - 응답 최상위가 JSON 배열이고, 그 안에 헤더 객체(resHeader/dsSch 등)와
 *    데이터 객체(dsList/dsList01 등)가 나뉘어 담긴다 — extractDataArray 로 흡수
 *  - 목록 API 는 cond[..] 문법이 없고 평범한 query param + ServiceKey 를 쓴다
 *  - 페이징이 PAGE/PG_SZ, 공고게시일·마감일(PAN_NT_ST_DT/CLSG_DT)이 필수
 *
 * 목록·상세·공급 응답 구조는 2026-09-04 라이브 호출 + 상세조회 활용가이드로 확인했다.
 * 목록 각 행이 상세/공급 재조회 코드(SPL_INF_TP_CD/CCR_CNNT_SYS_DS_CD/UPP_AIS_TP_CD/
 * AIS_TP_CD)를 담고 있어 providerParams 로 넘긴다.
 *
 * ★ LH open API 는 특별공급 <b>유형별 세대수</b>를 주지 않는다. 공급 API 는 주택형별
 * 총/금회 세대수까지, 상세 API 는 특별공급 유형의 존재·일정까지다. 그래서 여기서 만드는
 * {@link ExternalUnitType} 의 SupplyBreakdown 은 세대수가 아니라 "유형 존재"(countsKnown=false)다.
 * 판정(자격)은 세대수를 안 쓰므로 그대로 작동하고, 랭킹·표시에서만 "미상"으로 갈린다.
 * (CLAUDE.md "추측 금지" / "판정을 LLM 에 맡기지 않는다")
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LhClient implements AnnouncementSource {

    private static final String SOURCE_PREFIX = "LH-";
    private static final DateTimeFormatter LH_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 목록 응답에서 데이터가 아닌 헤더/검색조건 데이터셋으로 취급해 건너뛸 키 (소문자·부분일치) */
    private static final List<String> HEADER_KEYS = List.of("header", "dssch", "sch");

    /** 상세/공급 API 재조회에 필요한 목록 응답 코드 필드 */
    private static final List<String> RE_QUERY_FIELDS =
            List.of("SPL_INF_TP_CD", "CCR_CNNT_SYS_DS_CD", "UPP_AIS_TP_CD", "AIS_TP_CD");

    /** 공급 API dsList 의 주택형 필드 후보 (유형그룹마다 이름이 다름) */
    private static final String[] F_TYPE_NAME = {"HTY_NM", "HTY_NNA"};
    private static final String[] F_EXCL_AREA = {"RSDN_DDO_AR", "DDO_AR"};
    private static final String[] F_SUPPLY_AREA = {"SPL_AR"};
    private static final String[] F_NOW_COUNT = {"SIL_HSH_CNT", "NOW_HSH_CNT"};
    private static final String[] F_TOTAL_COUNT = {"TOT_HSH_CNT", "HSH_CNT"};
    private static final String[] F_PRICE = {"SIL_AMT"};

    private final RestClient publicDataRestClient;
    private final PublicDataProperties properties;
    private final PublicDataParser parser;
    private final LhSpecialSupplyMapper specialSupplyMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String sourceName() {
        return "LH";
    }

    @Override
    public boolean isEnabled() {
        return properties.getLh().isEnabled();
    }

    @Override
    public int maxPages() {
        return properties.getLh().getMaxPages();
    }

    // === 공고 목록 ===

    /** {@link AnnouncementSource} 계약 — 공고게시일 범위는 lookbackMonths 로 계산한다. */
    @Override
    public List<ExternalAnnouncement> fetchAnnouncements(int page) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(properties.getLh().getLookbackMonths());
        return fetchAnnouncements(from, to, page);
    }

    /** 공고게시일 {@code noticeFrom}~{@code noticeTo} 범위의 page 페이지. */
    public List<ExternalAnnouncement> fetchAnnouncements(LocalDate noticeFrom, LocalDate noticeTo, int page) {
        PublicDataProperties.Lh config = properties.getLh();
        if (!config.isEnabled()) {
            return List.of();
        }

        URI uri = UriComponentsBuilder
                .fromUriString(config.getNoticeListUrl())
                .queryParam("PG_SZ", config.getPerPage())
                .queryParam("PAGE", page)
                .queryParam("PAN_NT_ST_DT", LH_DATE.format(noticeFrom))
                .queryParam("CLSG_DT", LH_DATE.format(noticeTo))
                .queryParam("serviceKey", properties.getServiceKey())
                .build(true)   // serviceKey 는 이미 인코딩된 값 — 재인코딩하면 401. 이 호출엔 한글 파라미터가 없어 안전하다.
                .toUri();

        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseAnnouncements(body);
        } catch (Exception e) {
            log.warn("LH 공고 목록 조회 실패. page={}, msg={}", page, e.getMessage());
            return List.of();
        }
    }

    // === 주택형 ===

    /**
     * 공급 API(주택형·면적·세대수) + 상세 API(특별공급 유형)를 합쳐 주택형 목록을 만든다.
     * SupplyBreakdown 은 세대수가 아니라 "유형 존재"(countsKnown=false)로 채워진다.
     */
    @Override
    public List<ExternalUnitType> fetchUnitTypes(ExternalAnnouncement announcement) {
        if (!properties.getLh().isEnabled()) {
            return List.of();
        }
        Map<String, String> p = announcement.getProviderParams();
        if (p == null || p.get("SPL_INF_TP_CD") == null) {
            log.warn("LH 주택형 조회 불가 — providerParams 부족. panId={}", announcement.getPblancNo());
            return List.of();
        }

        Set<SpecialSupplyType> offeredTypes = fetchOfferedSpecialTypes(announcement.getPblancNo(), p);
        SupplyBreakdown breakdown = SupplyBreakdown.ofPresentTypes(offeredTypes);

        List<JsonNode> rows = fetchSupplyRows(announcement.getPblancNo(), p);
        List<ExternalUnitType> result = new ArrayList<>();
        int idx = 0;
        for (JsonNode row : rows) {
            String typeName = firstText(row, F_TYPE_NAME);
            if (typeName == null) continue;
            idx++;
            result.add(ExternalUnitType.builder()
                    .modelNo(String.format("%02d", idx))
                    .typeName(typeName)
                    .supplyArea(firstText(row, F_SUPPLY_AREA))
                    .generalSupplyCount(firstInt(row, F_NOW_COUNT))
                    .specialSupplyCount(0)   // 유형별 미상
                    .supplyBreakdown(breakdown)
                    .topAmount(wonToManwon(firstInt(row, F_PRICE)))
                    .build());
        }
        return result;
    }

    /**
     * 상세 API 응답에서 입주자모집공고문 원문(dsEtcInfo.PAN_DTL_CTS)을 꺼낸다.
     * 필드명이 유형그룹마다 다를 수 있어, PAN_DTL_CTS 키를 먼저 찾고 없으면
     * 응답에서 가장 긴 텍스트 값을 쓴다. HTML 태그·엔티티는 벗겨서 평문으로 만든다.
     */
    @Override
    public Optional<String> fetchNoticeContent(ExternalAnnouncement announcement) {
        if (!properties.getLh().isEnabled()) return Optional.empty();
        Map<String, String> p = announcement.getProviderParams();
        if (p == null || p.get("SPL_INF_TP_CD") == null) return Optional.empty();

        URI uri = detailUri(properties.getLh().getDetailUrl(), announcement.getPblancNo(), p);
        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseNoticeContent(body);
        } catch (Exception e) {
            log.warn("LH 공고문 원문 조회 실패. panId={}, msg={}", announcement.getPblancNo(), e.getMessage());
            return Optional.empty();
        }
    }

    /** package-private — 오프라인 테스트가 픽스처로 검증. */
    Optional<String> parseNoticeContent(byte[] body) throws Exception {
        if (body == null || body.length == 0) return Optional.empty();
        JsonNode root = objectMapper.readTree(body);

        // 1) PAN_DTL_CTS 키를 명시적으로 찾으면 신뢰한다 (짧아도 라벨은 아니다)
        String explicit = firstByKey(root, "pan_dtl_cts");
        if (explicit != null && !explicit.isBlank()) {
            String plain = stripHtml(explicit).strip();
            if (plain.length() >= 40) return Optional.of(plain);
        }

        // 2) 없으면 응답에서 가장 긴 텍스트 — 본문이라 할 만큼 길 때만
        String longest = longestText(root, 200);
        if (longest != null) {
            String plain = stripHtml(longest).strip();
            if (plain.length() >= 100) return Optional.of(plain);
        }
        return Optional.empty();
    }

    /** 키 이름이 소문자 기준 일치하는 첫 문자열 값 (트리 전체 탐색). */
    private String firstByKey(JsonNode node, String lowerKey) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().toLowerCase().equals(lowerKey) && e.getValue().isTextual()) {
                    return e.getValue().asText();
                }
                String found = firstByKey(e.getValue(), lowerKey);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = firstByKey(child, lowerKey);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String longestText(JsonNode node, int minLen) {
        String best = null;
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                best = longer(best, longestText(it.next().getValue(), minLen));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) best = longer(best, longestText(child, minLen));
        } else if (node.isTextual() && node.asText().length() >= minLen) {
            best = node.asText();
        }
        return best;
    }

    private String longer(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.length() > a.length() ? b : a;
    }

    private String stripHtml(String s) {
        return s.replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>|</div>|</li>|</tr>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n");
    }

    private Set<SpecialSupplyType> fetchOfferedSpecialTypes(String panId, Map<String, String> p) {
        URI uri = detailUri(properties.getLh().getDetailUrl(), panId, p);
        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseOfferedSpecialTypes(body);
        } catch (Exception e) {
            log.warn("LH 상세(특별공급 유형) 조회 실패. panId={}, msg={}", panId, e.getMessage());
            return Set.of();
        }
    }

    private List<JsonNode> fetchSupplyRows(String panId, Map<String, String> p) {
        URI uri = detailUri(properties.getLh().getSupplyUrl(), panId, p);
        try {
            byte[] body = publicDataRestClient.get().uri(uri).retrieve().body(byte[].class);
            return parseSupplyRows(body);
        } catch (Exception e) {
            log.warn("LH 공급(주택형) 조회 실패. panId={}, msg={}", panId, e.getMessage());
            return List.of();
        }
    }

    private URI detailUri(String baseUrl, String panId, Map<String, String> p) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("SPL_INF_TP_CD", p.get("SPL_INF_TP_CD"))
                .queryParam("CCR_CNNT_SYS_DS_CD", p.get("CCR_CNNT_SYS_DS_CD"))
                .queryParam("PAN_ID", panId)
                .queryParam("UPP_AIS_TP_CD", p.get("UPP_AIS_TP_CD"))
                .queryParam("AIS_TP_CD", p.getOrDefault("AIS_TP_CD", ""))
                .queryParam("serviceKey", properties.getServiceKey())
                .build(true)
                .toUri();
    }

    // === parsing (테스트를 위해 package-private) ===

    List<ExternalAnnouncement> parseAnnouncements(byte[] body) throws Exception {
        if (body == null || body.length == 0) return List.of();

        JsonNode root = objectMapper.readTree(body);
        JsonNode rows = extractDataArray(root);
        if (rows == null) {
            log.warn("LH 목록 응답에서 데이터 배열을 찾지 못함: {}", snippet(root));
            return List.of();
        }

        List<ExternalAnnouncement> result = new ArrayList<>();
        for (JsonNode item : rows) {
            ExternalAnnouncement parsed = toExternal(item);
            if (parsed != null && parsed.isValid()) {
                result.add(parsed);
            }
        }
        return result;
    }

    /** 상세 응답의 dsSplScdl 에서 특별공급 유형 집합을 뽑는다. */
    Set<SpecialSupplyType> parseOfferedSpecialTypes(byte[] body) throws Exception {
        if (body == null || body.length == 0) return Set.of();
        JsonNode root = objectMapper.readTree(body);
        JsonNode scdl = findFieldArray(root, "dssplscdl");   // dsSplScdl (dsSplScdl01/02 는 토지라 제외)
        if (scdl == null) return Set.of();

        List<String> targets = new ArrayList<>();
        for (JsonNode row : scdl) {
            String name = parser.text(row, "HS_SBSC_ACP_TRG_CD_NM");
            if (name != null) targets.add(name);
        }
        return specialSupplyMapper.mapAll(targets);
    }

    /** 공급 응답의 dsList01~04 를 한 리스트로 합친다. (dsList*Nm 은 라벨행이라 제외) */
    List<JsonNode> parseSupplyRows(byte[] body) throws Exception {
        List<JsonNode> rows = new ArrayList<>();
        if (body == null || body.length == 0) return rows;

        JsonNode root = objectMapper.readTree(body);
        JsonNode container = root.isArray() ? lastObject(root) : root;
        if (container == null) return rows;

        Iterator<Map.Entry<String, JsonNode>> fields = container.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String key = e.getKey().toLowerCase();
            if (!key.startsWith("dslist") || key.endsWith("nm")) continue;
            if (!e.getValue().isArray()) continue;
            e.getValue().forEach(rows::add);
        }
        return rows;
    }

    private JsonNode lastObject(JsonNode array) {
        JsonNode found = null;
        for (JsonNode n : array) {
            if (n.isObject()) found = n;
        }
        return found;
    }

    /**
     * LH 응답에서 실제 데이터 배열을 꺼낸다.
     * 최상위가 배열이면 각 원소를, 객체면 그 자신을 훑어 "헤더가 아닌 첫 배열 필드"를 찾는다.
     */
    private JsonNode extractDataArray(JsonNode root) {
        if (root.isArray()) {
            for (JsonNode element : root) {
                JsonNode found = firstDataArray(element);
                if (found != null) return found;
            }
            return null;
        }
        return firstDataArray(root);
    }

    private JsonNode firstDataArray(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!entry.getValue().isArray()) continue;
            String key = entry.getKey().toLowerCase();
            boolean isHeader = HEADER_KEYS.stream().anyMatch(key::contains);
            if (!isHeader) return entry.getValue();
        }
        return null;
    }

    /** 최상위(배열/객체) 어디에 있든 이름이 정확히 일치하는(소문자 기준) 배열 필드를 찾는다. */
    private JsonNode findFieldArray(JsonNode root, String lowerName) {
        List<JsonNode> nodes = root.isArray() ? toList(root) : List.of(root);
        for (JsonNode n : nodes) {
            if (!n.isObject()) continue;
            Iterator<Map.Entry<String, JsonNode>> fields = n.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                if (e.getKey().toLowerCase().equals(lowerName) && e.getValue().isArray()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private List<JsonNode> toList(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        return list;
    }

    private ExternalAnnouncement toExternal(JsonNode item) {
        String panId = parser.text(item, "PAN_ID");
        String panNm = parser.text(item, "PAN_NM");
        if (panId == null || panNm == null) return null;

        Map<String, String> providerParams = new HashMap<>();
        for (String field : RE_QUERY_FIELDS) {
            String value = parser.text(item, field);
            if (value != null) providerParams.put(field, value);
        }

        return ExternalAnnouncement.builder()
                .externalId(SOURCE_PREFIX + panId)
                // LH 는 공고번호가 따로 없어 PAN_ID 로 대체. CCR 코드는 재조회 키라 houseManageNo 에도 둔다.
                .houseManageNo(parser.text(item, "CCR_CNNT_SYS_DS_CD"))
                .pblancNo(panId)
                .houseName(panNm)
                .houseType(HouseType.UNKNOWN)
                // LH 주택 공급은 전부 공공(국민주택)이다. 토지·상가는 주택이 아니라 UNKNOWN.
                .houseDetailType(isHousing(providerParams.get("UPP_AIS_TP_CD"))
                        ? HouseDetailType.PUBLIC : HouseDetailType.UNKNOWN)
                .regionName(parser.text(item, "CNP_CD_NM"))
                .noticeDate(parser.date(item, "PAN_NT_ST_DT"))
                .receptEndDate(parser.date(item, "CLSG_DT"))
                .noticeUrl(parser.text(item, "DTL_URL"))
                .providerParams(providerParams)
                .build();
    }

    // === helpers ===

    /** UPP_AIS_TP_CD: 05 분양주택 / 06 임대주택 / 39 신혼희망타운 / 13 주거복지 = 주택 */
    private boolean isHousing(String uppAisTpCd) {
        return List.of("05", "06", "13", "39").contains(uppAisTpCd);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = parser.text(node, f);
            if (v != null) return v;
        }
        return null;
    }

    private int firstInt(JsonNode node, String... fields) {
        for (String f : fields) {
            Integer v = parser.number(node, f);
            if (v != null) return v;
        }
        return 0;
    }

    /** LH 는 분양가를 원 단위로 준다. UnitType.topAmount 는 만원 단위. */
    private Integer wonToManwon(int won) {
        return won <= 0 ? null : won / 10_000;
    }

    private String snippet(JsonNode node) {
        String s = node.toString();
        return s.substring(0, Math.min(300, s.length()));
    }
}
