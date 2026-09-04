package com.portfolio.chungyak.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 자연어 추출 eval 한 건. {@code src/test/resources/eval/profile-extraction-cases.json} 에서 읽는다.
 *
 * {@code expect} 안의 값 형태:
 * <ul>
 *   <li>{@code true}/{@code false}/정수 → 정확히 일치해야 함</li>
 *   <li>{@code null} → 반드시 null 이어야 함 (문장에 근거가 없는 필드는 추측 금지 — CLAUDE.md 절대 규칙)</li>
 *   <li>{@code {"min":a,"max":b}} → 정수이고 [a,b] 범위여야 함 (개월 수 등 근사값)</li>
 *   <li>키 없음 → 검사하지 않음</li>
 * </ul>
 */
record ExtractionEvalCase(String id, String text, String note, String expectStatus,
                          List<FieldExpectation> expectations) {

    enum Kind { EXACT, NULL, RANGE }

    record FieldExpectation(String field, Kind kind, Object exact, int min, int max) {}

    static List<ExtractionEvalCase> loadAll() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ExtractionEvalCase.class.getResourceAsStream(
                "/eval/profile-extraction-cases.json")) {
            if (in == null) {
                throw new IllegalStateException("eval 데이터셋을 찾을 수 없음: /eval/profile-extraction-cases.json");
            }
            JsonNode root = mapper.readTree(in);
            List<ExtractionEvalCase> cases = new ArrayList<>();
            root.forEach(node -> cases.add(parse(node)));
            return cases;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("eval 데이터셋 로딩 실패", e);
        }
    }

    private static ExtractionEvalCase parse(JsonNode c) {
        List<FieldExpectation> exps = new ArrayList<>();
        c.path("expect").fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode v = entry.getValue();
            if (v.isNull()) {
                exps.add(new FieldExpectation(field, Kind.NULL, null, 0, 0));
            } else if (v.isObject()) {
                exps.add(new FieldExpectation(field, Kind.RANGE, null,
                        v.get("min").asInt(), v.get("max").asInt()));
            } else if (v.isBoolean()) {
                exps.add(new FieldExpectation(field, Kind.EXACT, v.asBoolean(), 0, 0));
            } else if (v.isInt()) {
                exps.add(new FieldExpectation(field, Kind.EXACT, v.asInt(), 0, 0));
            } else {
                throw new IllegalStateException("알 수 없는 expect 형태: " + field + " = " + v);
            }
        });
        return new ExtractionEvalCase(
                c.get("id").asText(),
                c.get("text").asText(),
                c.path("note").asText(""),
                c.path("expectStatus").asText("EXTRACTED"),
                exps);
    }
}
