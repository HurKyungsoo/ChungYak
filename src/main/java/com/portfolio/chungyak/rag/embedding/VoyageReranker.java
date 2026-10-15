package com.portfolio.chungyak.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Voyage rerank API — {@code POST /v1/rerank {query, documents, model}} → {@code data[].index·relevance_score}.
 *
 * 이용자가 기다리는 경로라 재시도하지 않는다. 실패하면 예외를 던지고, 호출부({@code DocumentQaService})가
 * 하이브리드 순서 그대로 답한다 — rerank 는 순서를 다듬을 뿐 답의 필수 조건이 아니다.
 */
public class VoyageReranker implements Reranker {

    private final RestClient restClient;
    private final RerankProperties properties;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public VoyageReranker(RestClient restClient, RerankProperties properties, String apiKey) {
        this.restClient = restClient;
        this.properties = properties;
        this.apiKey = apiKey;
    }

    @Override
    public List<Integer> order(String query, List<String> documents) {
        if (documents == null || documents.size() <= 1) {   // 후보 1개 이하면 다시 매길 게 없다 — 호출하지 않는다
            List<Integer> same = new ArrayList<>();
            for (int i = 0; documents != null && i < documents.size(); i++) same.add(i);
            return same;
        }
        double[] score = scores(query, documents);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < score.length; i++) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> score[i]).reversed());
        return order;
    }

    /** 문서별 관련도(0..1) — 공식 FAQ 검색(OfficialFaqSearch)이 기준값으로 거른다 */
    @Override
    public double[] scores(String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) return new double[0];
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("documents", documents);
        body.put("model", properties.model());
        body.put("truncation", true);
        String json = restClient.post()
                .uri(properties.baseUrl())
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        return parseScores(json, documents.size());
    }

    /** package-private — 응답 파싱만 오프라인으로 검증한다. 응답에 빠진 인덱스는 맨 뒤(−∞) */
    double[] parseScores(String json, int expected) {
        try {
            double[] score = new double[expected];
            Arrays.fill(score, Double.NEGATIVE_INFINITY);
            for (JsonNode d : mapper.readTree(json).path("data")) {
                int idx = d.path("index").asInt(-1);
                if (idx >= 0 && idx < expected) score[idx] = d.path("relevance_score").asDouble();
            }
            return score;
        } catch (Exception e) {
            throw new IllegalStateException("Voyage rerank 응답 파싱 실패", e);
        }
    }

    /** package-private — 기존 테스트용(점수 → 순서) */
    List<Integer> parseOrder(String json, int expected) {
        double[] score = parseScores(json, expected);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < expected; i++) order.add(i);
        order.sort(Comparator.comparingDouble((Integer i) -> score[i]).reversed());
        return order;
    }

    @Override
    public int candidates() {
        return properties.candidates();
    }

    @Override
    public String model() {
        return properties.model();
    }
}
