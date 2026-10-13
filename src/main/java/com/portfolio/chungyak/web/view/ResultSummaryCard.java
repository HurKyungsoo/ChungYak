package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.web.view.EligibilityResultView.AllocatedType;
import com.portfolio.chungyak.web.view.EligibilityResultView.MatchedUnitType;
import com.portfolio.chungyak.web.view.EligibilityResultView.TypeDecision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 판정 결과 맨 위 요약 카드 (#21) — AI 없이 늘 보이는 결정론적 요약.
 *
 * {@link EligibilityResultView} 만 보고 줄을 다시 세운다. 판정·격차 계산은 하지 않는다 —
 * 어느 줄에 들어갈지는 엔진 플래그(eligible / undetermined / fixable) 그대로 정한다.
 * 그래서 판정과 어긋날 수 없고, 아래 근거 카드의 내용을 한 줄씩만 끌어올린다(문장은 근거 카드에).
 *
 * @param applicable              신청 가능 유형 — 주택형별 배정을 유형 단위로 합산
 * @param qualifiedButUnavailable 자격은 되지만 이 공고에 배정이 없는 유형
 * @param nearestFix              "이것만 바뀌면 가능"(isFixable) 유형 중 보완이 가장 적은 하나 — 없으면 null
 * @param otherFixable            nearestFix 외의 fixable 유형
 * @param blocked                 지금 조건으로는 어려운 유형(미충족이고 fixable 아님) — 사유는 근거 카드에
 * @param needsInfo               입력이 부족해 판정을 보류한 유형
 * @param missingInputs           needsInfo 유형들이 요구하는 입력(중복 제거)
 */
public record ResultSummaryCard(
        List<Applicable> applicable,
        List<TypeRef> qualifiedButUnavailable,
        Fix nearestFix,
        List<TypeRef> otherFixable,
        List<TypeRef> blocked,
        List<TypeRef> needsInfo,
        List<String> missingInputs) {

    /** @param count 공고 전체 배정 세대 합계. 세대수를 모르는 주택형(LH)이 섞이면 null */
    public record Applicable(String typeName, String label, Integer count) {}

    public record Fix(String typeName, String label, List<String> hints) {}

    public record TypeRef(String typeName, String label) {}

    public boolean hasAnyMatch() {
        return !applicable.isEmpty();
    }

    public static ResultSummaryCard of(EligibilityResultView view) {
        Map<String, String> typeNameByLabel = new LinkedHashMap<>();
        view.allDecisions().forEach(d -> typeNameByLabel.put(d.typeLabel(), d.typeName()));

        List<TypeDecision> fixable = view.allDecisions().stream()
                .filter(TypeDecision::fixable)
                .sorted(Comparator.comparingInt(d -> d.improvementHints().size()))   // 안정 정렬 — 동률은 카드 순서
                .toList();
        Fix nearest = fixable.isEmpty() ? null : new Fix(fixable.get(0).typeName(), fixable.get(0).typeLabel(),
                List.copyOf(new LinkedHashSet<>(fixable.get(0).improvementHints())));

        List<TypeDecision> undetermined = view.allDecisions().stream()
                .filter(d -> !d.eligible() && d.undetermined())
                .toList();
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        undetermined.forEach(d -> missing.addAll(d.missingInputs()));

        return new ResultSummaryCard(
                applicable(view.matchedUnitTypes(), typeNameByLabel),
                view.qualifiedButUnavailable().stream().map(ResultSummaryCard::ref).toList(),
                nearest,
                fixable.stream().skip(1).map(ResultSummaryCard::ref).toList(),
                view.allDecisions().stream()
                        .filter(d -> !d.eligible() && !d.undetermined() && !d.fixable())
                        .map(ResultSummaryCard::ref).toList(),
                undetermined.stream().map(ResultSummaryCard::ref).toList(),
                List.copyOf(missing));
    }

    /** 주택형마다 갈린 배정을 유형별로 합산 — 처음 나온 순서 유지 */
    private static List<Applicable> applicable(List<MatchedUnitType> units, Map<String, String> typeNameByLabel) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        Map<String, Boolean> known = new LinkedHashMap<>();
        for (MatchedUnitType u : units) {
            for (AllocatedType t : u.applicableTypes()) {
                sum.merge(t.typeLabel(), t.allocatedCount(), Integer::sum);
                known.merge(t.typeLabel(), u.allocationCountKnown(), Boolean::logicalAnd);
            }
        }
        List<Applicable> out = new ArrayList<>();
        sum.forEach((label, count) -> out.add(new Applicable(
                typeNameByLabel.get(label), label, known.get(label) ? count : null)));
        return out;
    }

    private static TypeRef ref(TypeDecision d) {
        return new TypeRef(d.typeName(), d.typeLabel());
    }
}
