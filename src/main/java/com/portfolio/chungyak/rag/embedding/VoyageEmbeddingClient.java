package com.portfolio.chungyak.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link EmbeddingClient} 의 Voyage AI 구현 — {@code POST /v1/embeddings}.
 *
 * Voyage 는 공식 Java SDK 가 없어 {@link RestClient} 로 raw HTTP 를 친다.
 * 배치로 나눠 호출하고(기본 96개/요청), 응답의 {@code data[].embedding} 을
 * 요청 순서대로 되돌린다({@code data[].index} 로 재정렬).
 *
 * 429(rate limit)·5xx·연결 오류는 지수적 백오프로 재시도한다 —
 * 결제수단 미등록 계정은 3 RPM 로 제한되므로 인덱싱 배치가 이걸 자주 만난다.
 * 재시도를 다 쓰면 예외를 던지고, 인덱서가 그 공고만 스킵한다.
 */
@Slf4j
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final int MAX_RETRIES = 4;
    private static final long BASE_BACKOFF_MS = 22_000;   // 분당 제한이라 20초대가 적절
    private static final long MAX_BACKOFF_MS = 65_000;

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final VoyageProperties properties;
    private final long baseBackoffMs;

    public VoyageEmbeddingClient(RestClient voyageRestClient, VoyageProperties properties) {
        this(voyageRestClient, properties, BASE_BACKOFF_MS);
    }

    /** 테스트용 — 백오프 기준 시간을 줄여 재시도 로직을 빠르게 검증한다. */
    VoyageEmbeddingClient(RestClient voyageRestClient, VoyageProperties properties, long baseBackoffMs) {
        this.restClient = voyageRestClient;
        this.properties = properties;
        this.baseBackoffMs = baseBackoffMs;
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public List<float[]> embed(List<String> texts, InputType type) {
        if (texts == null || texts.isEmpty()) return List.of();

        List<float[]> out = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += properties.batchSize()) {
            int end = Math.min(start + properties.batchSize(), texts.size());
            out.addAll(embedBatch(texts.subList(start, end), type));
        }
        return out;
    }

    private List<float[]> embedBatch(List<String> batch, InputType type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("input", batch);
        body.put("input_type", type == InputType.QUERY ? "query" : "document");

        return parseEmbeddings(postWithRetry(body), batch.size());
    }

    private String postWithRetry(Map<String, Object> body) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return restClient.post()
                        .uri(properties.baseUrl())
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (HttpClientErrorException.TooManyRequests
                     | HttpServerErrorException | ResourceAccessException e) {
                last = e;
                if (attempt == MAX_RETRIES) break;
                long wait = Math.min(MAX_BACKOFF_MS, baseBackoffMs * (attempt + 1))
                        + ThreadLocalRandom.current().nextLong(0, Math.max(1, baseBackoffMs / 8));
                log.warn("Voyage 임베딩 재시도 {}/{} — {}초 후 ({})",
                        attempt + 1, MAX_RETRIES, wait / 1000, shortMessage(e));
                sleep(wait);
            }
        }
        throw last;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("임베딩 재시도 대기 중 인터럽트", ie);
        }
    }

    private static String shortMessage(RuntimeException e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m.substring(0, Math.min(120, m.length()));
    }

    /** package-private — 오프라인 계약 테스트가 실제 Voyage 응답 픽스처로 검증한다. */
    List<float[]> parseEmbeddings(String json, int expected) {
        try {
            JsonNode data = mapper.readTree(json).path("data");
            if (!data.isArray() || data.size() != expected) {
                throw new IllegalStateException("Voyage 응답 data 크기 불일치: expected " + expected
                        + ", got " + (data.isArray() ? data.size() : "non-array"));
            }
            float[][] ordered = new float[expected][];
            for (JsonNode item : data) {
                int idx = item.path("index").asInt();
                JsonNode vec = item.path("embedding");
                float[] v = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) v[i] = (float) vec.get(i).asDouble();
                ordered[idx] = v;
            }
            return List.of(ordered);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Voyage 응답 파싱 실패", e);
        }
    }
}
