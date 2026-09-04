package com.portfolio.chungyak.llm;

import com.portfolio.chungyak.llm.ExtractionEvalScorer.CaseScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * eval 채점 로직 + 데이터셋 자체를 LLM 없이 검증한다.
 *
 * "오라클(정답을 그대로 넣으면 100%)"과 "널(빈 결과면 낮은 점수)"을 통과시켜
 * 채점기가 실제로 뭔가를 재고 있는지 확인한다 (build-eval 가이드의 sanity 체크).
 */
class ExtractionEvalScorerTest {

    @Test
    @DisplayName("데이터셋이 온전하다 — 15건 이상, id 유일, 케이스마다 검증 항목 존재")
    void datasetIsWellFormed() {
        List<ExtractionEvalCase> cases = ExtractionEvalCase.loadAll();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(15);

        Set<String> ids = cases.stream().map(ExtractionEvalCase::id).collect(Collectors.toSet());
        assertThat(ids).hasSameSizeAs(cases);

        for (ExtractionEvalCase c : cases) {
            assertThat(c.text()).as("case %s text", c.id()).isNotBlank();
            boolean checksSomething = !c.expectations().isEmpty()
                    || !c.expectStatus().equals("EXTRACTED");
            assertThat(checksSomething).as("case %s 는 아무것도 검증하지 않음", c.id()).isTrue();
        }

        // 모든 필드에 접근자가 있어야 한다 (오타 방지)
        for (ExtractionEvalCase c : cases) {
            for (var exp : c.expectations()) {
                assertThat(ExtractionEvalScorer.ACCESSORS).containsKey(exp.field());
            }
        }
    }

    @Test
    @DisplayName("null 원칙 케이스가 데이터셋에 충분히 있다 (추측 금지가 이 프로젝트의 핵심)")
    void datasetExercisesNullDiscipline() {
        long nullExpectations = ExtractionEvalCase.loadAll().stream()
                .flatMap(c -> c.expectations().stream())
                .filter(e -> e.kind() == ExtractionEvalCase.Kind.NULL)
                .count();
        assertThat(nullExpectations).isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("오라클 — 기대와 정확히 일치하는 프로필은 케이스 통과")
    void oracleProfilePasses() {
        ExtractionEvalCase c = new ExtractionEvalCase("t", "..", "", "EXTRACTED", List.of(
                new ExtractionEvalCase.FieldExpectation("married", ExtractionEvalCase.Kind.EXACT, true, 0, 0),
                new ExtractionEvalCase.FieldExpectation("childCount", ExtractionEvalCase.Kind.EXACT, 2, 0, 0),
                new ExtractionEvalCase.FieldExpectation("monthsSinceMarriage", ExtractionEvalCase.Kind.RANGE, null, 30, 42),
                new ExtractionEvalCase.FieldExpectation("hasNewborn", ExtractionEvalCase.Kind.NULL, null, 0, 0)));

        ExtractedProfile p = new ExtractedProfile(true, 36, 2, null, null, null, null, null, null);
        CaseScore s = ExtractionEvalScorer.score(c, "EXTRACTED", p);

        assertThat(s.passed()).isTrue();
        assertThat(s.fields()).allMatch(ExtractionEvalScorer.FieldResult::correct);
    }

    @Test
    @DisplayName("널 — 빈(전부 null) 프로필은 값 기대를 못 맞춘다")
    void emptyProfileFailsValueExpectations() {
        ExtractionEvalCase c = new ExtractionEvalCase("t", "..", "", "EXTRACTED", List.of(
                new ExtractionEvalCase.FieldExpectation("married", ExtractionEvalCase.Kind.EXACT, true, 0, 0),
                new ExtractionEvalCase.FieldExpectation("hasNewborn", ExtractionEvalCase.Kind.NULL, null, 0, 0)));

        ExtractedProfile empty = new ExtractedProfile(null, null, null, null, null, null, null, null, null);
        CaseScore s = ExtractionEvalScorer.score(c, "EXTRACTED", empty);

        assertThat(s.passed()).isFalse();
        // married=true 기대는 실패, hasNewborn=null 기대는 성공
        assertThat(s.fields()).filteredOn(f -> f.field().equals("married"))
                .allMatch(f -> !f.correct());
        assertThat(s.fields()).filteredOn(f -> f.field().equals("hasNewborn"))
                .allMatch(ExtractionEvalScorer.FieldResult::correct);
    }

    @Test
    @DisplayName("추출 자체가 실패하면(profile=null) 값 기대는 전부 오답")
    void extractionFailureFailsAllFieldExpectations() {
        ExtractionEvalCase c = new ExtractionEvalCase("t", "..", "", "EXTRACTED", List.of(
                new ExtractionEvalCase.FieldExpectation("houseless", ExtractionEvalCase.Kind.NULL, null, 0, 0)));

        CaseScore s = ExtractionEvalScorer.score(c, "FAILED", null);

        assertThat(s.statusOk()).isFalse();
        assertThat(s.fields()).allMatch(f -> !f.correct());
    }

    @Test
    @DisplayName("expectStatus=FAILED 케이스는 그 상태만 맞으면 통과")
    void expectedFailureCasePassesOnStatus() {
        ExtractionEvalCase c = new ExtractionEvalCase("unrelated", "점심 뭐 먹지", "", "FAILED", List.of());

        assertThat(ExtractionEvalScorer.score(c, "FAILED", null).passed()).isTrue();
        assertThat(ExtractionEvalScorer.score(c, "EXTRACTED",
                new ExtractedProfile(true, null, null, null, null, null, null, null, null)).passed()).isFalse();
    }

    @Test
    @DisplayName("스코어카드 집계 — 정확도·null 준수·통과율")
    void scorecardAggregates() {
        ExtractionEvalCase pass = new ExtractionEvalCase("p", "..", "", "EXTRACTED", List.of(
                new ExtractionEvalCase.FieldExpectation("married", ExtractionEvalCase.Kind.EXACT, true, 0, 0),
                new ExtractionEvalCase.FieldExpectation("childCount", ExtractionEvalCase.Kind.NULL, null, 0, 0)));
        ExtractionEvalCase fail = new ExtractionEvalCase("f", "..", "", "EXTRACTED", List.of(
                new ExtractionEvalCase.FieldExpectation("married", ExtractionEvalCase.Kind.EXACT, true, 0, 0),
                new ExtractionEvalCase.FieldExpectation("childCount", ExtractionEvalCase.Kind.NULL, null, 0, 0)));

        ExtractedProfile good = new ExtractedProfile(true, null, null, null, null, null, null, null, null);
        ExtractedProfile bad = new ExtractedProfile(false, null, 3, null, null, null, null, null, null);

        var card = ExtractionEvalScorecard.of(List.of(
                ExtractionEvalScorer.score(pass, "EXTRACTED", good),
                ExtractionEvalScorer.score(fail, "EXTRACTED", bad)), "test-model");

        assertThat(card.caseTotal()).isEqualTo(2);
        assertThat(card.casePassed()).isEqualTo(1);
        assertThat(card.fieldCorrect()).isEqualTo(2);   // good 의 2개
        assertThat(card.fieldTotal()).isEqualTo(4);
        assertThat(card.nullCorrect()).isEqualTo(1);    // good 의 childCount=null
        assertThat(card.nullTotal()).isEqualTo(2);
        assertThat(card.render()).contains("자연어 추출 eval").contains("[f]");
    }
}
