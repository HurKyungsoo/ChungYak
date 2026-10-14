package com.portfolio.chungyak.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.chungyak.external.CompetitionRecords.Rate;
import com.portfolio.chungyak.external.CompetitionRecords.Score;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 청약홈 당첨가점·경쟁률 API (#34) — 최신 공고 순으로 페이지 단위로 받는다(odcloud: {@code page}/{@code perPage}).
 * 실패하거나 URL·키가 없으면 빈 페이지(앱은 그대로 돈다). 파싱 실패 행은 스킵한다(외부 API 규칙).
 */
@Slf4j
@Component
public class ApplyhomeCompetitionClient {

    private final RestClient publicDataRestClient;
    private final PublicDataProperties properties;
    private final ObjectMapper objectMapper;

    public ApplyhomeCompetitionClient(@Qualifier("publicDataRestClient") RestClient publicDataRestClient,
                                      PublicDataProperties properties, ObjectMapper objectMapper) {
        this.publicDataRestClient = publicDataRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 한 페이지와 전체 건수 — 마지막 페이지를 알려고 */
    public record Page<T>(List<T> rows, int totalCount) {
        static <T> Page<T> empty() {
            return new Page<>(List.of(), 0);
        }
    }

    public boolean isEnabled() {
        var a = properties.getApplyhome();
        return notBlank(a.getCompetitionScoreUrl()) && notBlank(a.getCompetitionRateUrl())
                && notBlank(properties.getServiceKey()) && !"REPLACE_ME".equals(properties.getServiceKey());
    }

    public Page<Score> fetchScores(int page, int perPage) {
        return fetch(properties.getApplyhome().getCompetitionScoreUrl(), page, perPage, this::parseScores);
    }

    public Page<Rate> fetchRates(int page, int perPage) {
        return fetch(properties.getApplyhome().getCompetitionRateUrl(), page, perPage, this::parseRates);
    }

    private interface Parser<T> {
        Page<T> parse(byte[] body) throws Exception;
    }

    private <T> Page<T> fetch(String url, int page, int perPage, Parser<T> parser) {
        if (!isEnabled()) return Page.empty();
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("page", page)
                .queryParam("perPage", perPage)
                .queryParam("serviceKey", properties.getServiceKey())
                .build(true)   // serviceKey 는 이미 인코딩된 값 — 재인코딩하면 401 (cond 파라미터를 쓰지 않으므로 build(true) 가 맞다)
                .toUri();
        try {
            return parser.parse(publicDataRestClient.get().uri(uri).retrieve().body(byte[].class));
        } catch (Exception e) {
            log.warn("청약홈 경쟁률·가점 조회 실패 page={} — {}", page, e.getMessage());
            return Page.empty();
        }
    }

    Page<Score> parseScores(byte[] body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<Score> out = new ArrayList<>();
        for (JsonNode r : root.path("data")) {
            try {
                out.add(new Score(text(r, "HOUSE_MANAGE_NO"), text(r, "MODEL_NO"), text(r, "HOUSE_TY"),
                        text(r, "RESIDE_SECD"), score(r, "LWET_SCORE"), score(r, "AVRG_SCORE"), score(r, "TOP_SCORE")));
            } catch (RuntimeException e) {
                log.debug("가점 행 스킵: {}", e.getMessage());
            }
        }
        return new Page<>(out, root.path("totalCount").asInt(0));
    }

    Page<Rate> parseRates(byte[] body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<Rate> out = new ArrayList<>();
        for (JsonNode r : root.path("data")) {
            try {
                String rate = r.path("CMPET_RATE").asText("").strip();
                out.add(new Rate(text(r, "HOUSE_MANAGE_NO"), text(r, "MODEL_NO"), text(r, "HOUSE_TY"),
                        r.path("SUBSCRPT_RANK_CODE").asInt(), text(r, "RESIDE_SECD"),
                        r.path("SUPLY_HSHLDCO").asInt(0), count(r.path("REQ_CNT").asText("")),
                        rate.length() > 20 ? rate.substring(0, 20) : rate));
            } catch (RuntimeException e) {
                log.debug("경쟁률 행 스킵: {}", e.getMessage());
            }
        }
        return new Page<>(out, root.path("totalCount").asInt(0));
    }

    private static String text(JsonNode r, String field) {
        String v = r.path(field).asText(null);
        if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " 없음");
        return v.strip();
    }

    /** "49" → 49, "0"·빈 값·숫자 아님 → 0 (추첨·미달·미집계와 같게 본다) */
    private static int score(JsonNode r, String field) {
        String v = r.path(field).asText("").replace(",", "").strip();
        return v.matches("\\d+") ? Integer.parseInt(v) : 0;
    }

    private static Integer count(String v) {
        String s = v.replace(",", "").strip();
        return s.matches("\\d+") ? Integer.valueOf(s) : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
