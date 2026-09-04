package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.llm.ExtractionEvalCase.FieldExpectation;
import com.portfolio.chungyak.llm.ExtractionEvalCase.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@link ExtractedProfile} 를 기대값과 대조해 점수를 낸다.
 *
 * 순수 함수 — LLM 없이 단위테스트로 검증한다({@link ExtractionEvalScorerTest}).
 */
final class ExtractionEvalScorer {

    /** 필드명 → ExtractedProfile 접근자. */
    static final Map<String, Function<ExtractedProfile, Object>> ACCESSORS = Map.of(
            "married", ExtractedProfile::married,
            "monthsSinceMarriage", ExtractedProfile::monthsSinceMarriage,
            "childCount", ExtractedProfile::childCount,
            "hasNewborn", ExtractedProfile::hasNewborn,
            "houseless", ExtractedProfile::houseless,
            "accountMonths", ExtractedProfile::accountMonths,
            "everOwnedHouse", ExtractedProfile::everOwnedHouse,
            "supportingOldParents", ExtractedProfile::supportingOldParents,
            "householdHead", ExtractedProfile::householdHead);

    private ExtractionEvalScorer() {}

    record FieldResult(String field, Kind kind, boolean correct, Object expected, Object actual) {}

    record CaseScore(String id, boolean statusOk, List<FieldResult> fields) {
        boolean passed() {
            return statusOk && fields.stream().allMatch(FieldResult::correct);
        }
    }

    static boolean checkField(FieldExpectation exp, Object actual) {
        return switch (exp.kind()) {
            case NULL -> actual == null;
            case EXACT -> Objects.equals(exp.exact(), actual);
            case RANGE -> actual instanceof Integer i && i >= exp.min() && i <= exp.max();
        };
    }

    static CaseScore score(ExtractionEvalCase c, String actualStatus, ExtractedProfile profile) {
        boolean statusOk = c.expectStatus().equals(actualStatus);
        List<FieldResult> fields = new ArrayList<>();
        for (FieldExpectation exp : c.expectations()) {
            Object actual = profile == null ? null : ACCESSORS.get(exp.field()).apply(profile);
            Object expected = switch (exp.kind()) {
                case NULL -> null;
                case EXACT -> exp.exact();
                case RANGE -> "[" + exp.min() + ".." + exp.max() + "]";
            };
            // 추출이 실패해 profile 이 null 인데 필드 기대가 있으면, NULL 기대만 우연히 맞는 셈이 되므로
            // "값을 뽑았어야 했는데 못 뽑음" 은 오답으로 본다.
            boolean correct = profile != null && checkField(exp, actual);
            fields.add(new FieldResult(exp.field(), exp.kind(), correct, expected, actual));
        }
        return new CaseScore(c.id(), statusOk, fields);
    }
}
