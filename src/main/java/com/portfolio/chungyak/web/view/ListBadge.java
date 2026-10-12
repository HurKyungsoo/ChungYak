package com.portfolio.chungyak.web.view;

import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.rule.EligibilityDecision;
import com.portfolio.chungyak.rule.EligibilityEngine.MatchResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공고 목록 카드의 "내 자격" 배지 — 저장된 조건으로 엔진이 낸 판정을 한 줄로 옮긴다.
 *
 * 판정은 {@link com.portfolio.chungyak.rule.EligibilityEngine} 이 이미 끝냈다. 여기서는 결과를 넷 중 하나로 고를 뿐이다:
 * <ul>
 *   <li>{@code ELIGIBLE} — 신청 가능한 유형이 있다(물량 있는 주택형 기준, 엔진의 matches)</li>
 *   <li>{@code FIXABLE} — 지금은 안 되지만 미충족이 전부 기간·선택으로 메울 수 있다
 *       ({@link EligibilityDecision#isFixable()}, 이 공고에 물량이 있는 유형만)</li>
 *   <li>{@code NEEDS_INFO} — 입력이 부족해 판정을 못 한 유형이 있다(빠른 진단 3문항만 넣은 경우 등).
 *       "해당 없음"으로 보이면 틀린 말이 된다</li>
 *   <li>{@code NONE} — 물량 있는 유형이 전부 미충족</li>
 * </ul>
 *
 * @param types ELIGIBLE·FIXABLE 일 때 그 유형 이름들
 * @param hints FIXABLE 일 때 엔진이 남긴 "이렇게 하면 된다" 문장(중복 제거) — 화면이 툴팁으로 보여 준다
 */
public record ListBadge(State state, List<String> types, List<String> hints) {

    /** UNKNOWN — 유형별 물량을 몰라 배지를 그리지 않는다 */
    public enum State { ELIGIBLE, FIXABLE, NEEDS_INFO, NONE, UNKNOWN }

    public static ListBadge of(MatchResult result) {
        List<String> eligible = result.matches().stream()
                .flatMap(m -> m.applicableTypes().stream())
                .distinct().sorted().map(SpecialSupplyType::getLabel).toList();
        if (!eligible.isEmpty()) return new ListBadge(State.ELIGIBLE, eligible, List.of());

        // 물량이 없는 유형은 자격을 메워도 이 공고엔 넣을 데가 없다 — 배지에 올리지 않는다
        Set<SpecialSupplyType> offered = Set.copyOf(result.announcement().visibleSupplyTypes());
        // 유형별 물량을 아예 모르는 공고(신혼희망타운 총량만·특공 없는 공고)는 "해당 없음"이라 단정하지 않는다
        if (offered.isEmpty()) return new ListBadge(State.UNKNOWN, List.of(), List.of());
        List<Map.Entry<SpecialSupplyType, EligibilityDecision>> relevant = result.decisions().entrySet().stream()
                .filter(e -> offered.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .toList();

        List<Map.Entry<SpecialSupplyType, EligibilityDecision>> fixable = relevant.stream()
                .filter(e -> e.getValue().isFixable()).toList();
        if (!fixable.isEmpty()) {
            return new ListBadge(State.FIXABLE,
                    fixable.stream().map(e -> e.getKey().getLabel()).toList(),
                    fixable.stream().flatMap(e -> e.getValue().getImprovementHints().stream()).distinct().toList());
        }
        if (relevant.stream().anyMatch(e -> e.getValue().isUndetermined())) {
            return new ListBadge(State.NEEDS_INFO, List.of(), List.of());
        }
        return new ListBadge(State.NONE, List.of(), List.of());
    }
}
