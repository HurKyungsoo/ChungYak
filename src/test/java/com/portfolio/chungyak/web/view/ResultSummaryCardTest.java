package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.web.view.EligibilityResultView.AllocatedType;
import com.portfolio.chungyak.web.view.EligibilityResultView.MatchedUnitType;
import com.portfolio.chungyak.web.view.EligibilityResultView.TypeDecision;
import com.portfolio.chungyak.web.view.ResultSummaryCard.Applicable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요약 카드는 엔진 플래그(eligible/undetermined/fixable) 그대로 줄을 나눈다 — 판정을 새로 하지 않는다.
 */
class ResultSummaryCardTest {

    private static TypeDecision eligible(String name, String label) {
        return decision(name, label, true, List.of(), List.of(), false);
    }

    private static TypeDecision fixable(String name, String label, String... hints) {
        return decision(name, label, false, List.of(), List.of(hints), true);
    }

    private static TypeDecision blocked(String name, String label, String... hints) {
        return decision(name, label, false, List.of(), List.of(hints), false);
    }

    private static TypeDecision undetermined(String name, String label, String... missing) {
        return decision(name, label, false, List.of(missing), List.of(), false);
    }

    private static TypeDecision decision(String name, String label, boolean eligible,
                                         List<String> missing, List<String> hints, boolean fixable) {
        return new TypeDecision(name, label, eligible, !missing.isEmpty(),
                List.of(), List.of(), missing, hints, List.of(),
                List.of(), List.of(), missing, hints, null, fixable);
    }

    private static MatchedUnitType unit(String typeName, boolean known, AllocatedType... types) {
        int total = 0;
        for (AllocatedType t : types) total += t.allocatedCount();
        return new MatchedUnitType(typeName, "109.57", known, total, List.of(types));
    }

    private static EligibilityResultView view(List<MatchedUnitType> units, List<TypeDecision> qbu,
                                              List<TypeDecision> all) {
        return new EligibilityResultView(1L, "테스트 단지", false, !units.isEmpty(), units, qbu, all);
    }

    @Test
    @DisplayName("신청 가능 유형은 주택형별 배정을 유형 단위로 합산한다")
    void mergesAllocationsAcrossUnitTypes() {
        var card = ResultSummaryCard.of(view(
                List.of(unit("084A", true, new AllocatedType("신혼부부", 30), new AllocatedType("생애최초", 10)),
                        unit("059A", true, new AllocatedType("신혼부부", 17), new AllocatedType("생애최초", 12))),
                List.of(),
                List.of(eligible("NEWLYWED", "신혼부부"), eligible("FIRST_TIME", "생애최초"))));

        assertThat(card.hasAnyMatch()).isTrue();
        assertThat(card.applicable()).containsExactly(
                new Applicable("NEWLYWED", "신혼부부", 47),
                new Applicable("FIRST_TIME", "생애최초", 22));
    }

    @Test
    @DisplayName("세대수를 모르는 주택형(LH)이 섞이면 세대수는 null — 0 세대로 보이지 않게")
    void unknownAllocationIsNull() {
        var card = ResultSummaryCard.of(view(
                List.of(unit("A", false, new AllocatedType("신혼부부", 0))),
                List.of(),
                List.of(eligible("NEWLYWED", "신혼부부"))));

        assertThat(card.applicable()).containsExactly(new Applicable("NEWLYWED", "신혼부부", null));
    }

    @Test
    @DisplayName("가장 가까운 보완 = fixable 유형 중 힌트가 가장 적은 것, 나머지는 otherFixable")
    void nearestFixIsFixableWithFewestHints() {
        var card = ResultSummaryCard.of(view(List.of(), List.of(), List.of(
                fixable("FIRST_TIME", "생애최초", "통장 3개월", "거주 6개월"),
                fixable("OLD_PARENTS", "노부모부양", "통장 3개월"))));

        assertThat(card.nearestFix().label()).isEqualTo("노부모부양");
        assertThat(card.nearestFix().hints()).containsExactly("통장 3개월");
        assertThat(card.otherFixable()).extracting(ResultSummaryCard.TypeRef::label).containsExactly("생애최초");
    }

    @Test
    @DisplayName("힌트가 있어도 fixable 이 아니면(메울 수 없는 미충족 섞임) 보완이 아니라 '어려움' 줄로")
    void mixedFailureIsBlockedNotFix() {
        var card = ResultSummaryCard.of(view(List.of(), List.of(), List.of(
                blocked("NEWLYWED", "신혼부부", "통장 3개월"))));

        assertThat(card.nearestFix()).isNull();
        assertThat(card.blocked()).extracting(ResultSummaryCard.TypeRef::label).containsExactly("신혼부부");
    }

    @Test
    @DisplayName("판정 보류 유형은 정보 필요 줄로, 필요한 입력은 중복 없이")
    void undeterminedGoesToNeedsInfo() {
        var card = ResultSummaryCard.of(view(List.of(), List.of(), List.of(
                undetermined("MULTI_CHILD", "다자녀가구", "총자산", "자동차가액"),
                undetermined("NEWBORN", "신생아", "총자산"))));

        assertThat(card.needsInfo()).hasSize(2);
        assertThat(card.missingInputs()).containsExactly("총자산", "자동차가액");
        assertThat(card.blocked()).isEmpty();
        assertThat(card.nearestFix()).isNull();
    }

    @Test
    @DisplayName("자격은 되지만 물량 없는 유형은 따로 — 신청 가능·어려움 어디에도 섞이지 않는다")
    void qualifiedButUnavailableSeparate() {
        TypeDecision multi = eligible("MULTI_CHILD", "다자녀가구");
        var card = ResultSummaryCard.of(view(List.of(), List.of(multi), List.of(multi)));

        assertThat(card.hasAnyMatch()).isFalse();
        assertThat(card.qualifiedButUnavailable()).extracting(ResultSummaryCard.TypeRef::label).containsExactly("다자녀가구");
        assertThat(card.blocked()).isEmpty();
        assertThat(card.needsInfo()).isEmpty();
    }
}
