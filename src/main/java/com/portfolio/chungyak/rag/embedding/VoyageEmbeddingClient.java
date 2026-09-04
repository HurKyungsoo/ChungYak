package com.portfolio.chungyak.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EmbeddingClient} 의 Voyage AI 구현 — {@code POST /v1/embeddings}.
 *
 * Voyage 는 공식 Java SDK 가 없어 {@link RestClient} 로 raw HTTP 를 친다.
 * 배치로 나눠 호출하고(기본 96개/요청), 응답의 {@code data[].embedding} 을
 * 요청 순서대로 되돌린다({@code data[].index} 로 재정렬).
 * 실패는 예외로 던진다 — 인덱서가 잡아 그 공고만 스킵한다.
 */
@Slf4j
public class VoyageEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final VoyageProperties properties;

    public VoyageEmbeddingClient(RestClient voyageRestClient, VoyageProperties properties) {
        this.restClient = voyageRestClient;
        this.properties = properties;
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

        String response = restClient.post()
                .uri(properties.baseUrl())
                .header("Authorization", "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseEmbeddings(response, batch.size());
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
