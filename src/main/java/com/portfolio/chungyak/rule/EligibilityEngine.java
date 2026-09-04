package com.portfolio.chungyak.rule;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.UnitType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 규칙 엔진.
 *
 * 이 프로젝트의 핵심 설계 판단이 여기 있다 —
 * 자격 판정은 전부 이 클래스 안에서 결정론적으로 끝난다. LLM 은 관여하지 않는다.
 *
 * 이유: 판정을 LLM 에 맡기면 같은 조건에 다른 답이 나오고, 근거를 지어낸다.
 * 청약은 "될 수도 있다"가 아니라 되거나 안 되거나 둘 중 하나이고,
 * 틀린 답의 대가가 크다(청약통장을 쓰고 재당첨 제한이 걸린다).
 *
 * LLM 의 역할은 앞뒤로만 있다:
 *   앞 — 자연어 질문에서 ApplicantProfile 을 추출
 *   뒤 — 이 엔진이 낸 판정과 이유를 자연어로 풀어 설명
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EligibilityEngine {

    private final List<EligibilityRule> rules;

    /**
     * 한 공고의 모든 주택형에 대해 신청 가능한 특별공급 유형을 찾는다.
     *
     * 판정은 두 단계다.
     *  1) 그 유형에 배정 세대가 있는가 (공고 데이터)
     *  2) 신청자가 그 유형의 자격을 갖췄는가 (규칙)
     * 둘 다 만족해야 실제로 넣을 수 있다.
     */
    public MatchResult evaluate(ApplicantProfile profile, Announcement announcement) {
        Map<SpecialSupplyType, EligibilityDecision> decisions = new EnumMap<>(SpecialSupplyType.class);

        for (EligibilityRule rule : rules) {
            EligibilityDecision decision = rule.evaluate(profile, announcement);
            decisions.put(rule.supportedType(), decision);
        }

        List<UnitMatch> matches = new ArrayList<>();
        for (UnitType unitType : announcement.getUnitTypes()) {
            List<SpecialSupplyType> applicable = new ArrayList<>();

            for (Map.Entry<SpecialSupplyType, EligibilityDecision> entry : decisions.entrySet()) {
                boolean qualified = entry.getValue().isEligible();
                boolean allocated = unitType.getSupplyBreakdown() != null
                        && unitType.getSupplyBreakdown().hasAllocation(entry.getKey());

                if (qualified && allocated) {
                    applicable.add(entry.getKey());
                }
            }

            if (!applicable.isEmpty()) {
                matches.add(new UnitMatch(unitType, applicable));
            }
        }

        return new MatchResult(announcement, decisions, matches);
    }

    public record UnitMatch(UnitType unitType, List<SpecialSupplyType> applicableTypes) {

        /**
         * 배정 세대수를 아는 소스인지(청약홈 true / LH false).
         * false 면 "유형은 있으나 몇 세대인지 모른다"는 뜻이고 totalAllocated 는 0 이다.
         */
        public boolean allocationCountKnown() {
            return unitType.getSupplyBreakdown() != null
                    && unitType.getSupplyBreakdown().isCountsKnown();
        }

        /** 이 주택형에서 신청 가능한 유형들의 배정 세대수 합. 세대수 미상이면 0. */
        public int totalAllocated() {
            if (!allocationCountKnown()) return 0;
            return applicableTypes.stream()
                    .mapToInt(t -> unitType.getSupplyBreakdown().countOf(t))
                    .sum();
        }
    }

    public record MatchResult(
            Announcement announcement,
            Map<SpecialSupplyType, EligibilityDecision> decisions,
            List<UnitMatch> matches) {

        public boolean hasAnyMatch() {
            return !matches.isEmpty();
        }

        /** 배정 세대수가 가장 많은 주택형 — "어디에 넣는 게 유리한가" */
        public UnitMatch bestMatch() {
            return matches.stream()
                    .max((a, b) -> Integer.compare(a.totalAllocated(), b.totalAllocated()))
                    .orElse(null);
        }

        /** 자격은 되는데 이 공고에 물량이 없는 유형 — 안내에 필요하다 */
        public List<SpecialSupplyType> qualifiedButUnavailable() {
            List<SpecialSupplyType> result = new ArrayList<>();
            for (Map.Entry<SpecialSupplyType, EligibilityDecision> e : decisions.entrySet()) {
                if (!e.getValue().isEligible()) continue;
                boolean anywhere = matches.stream()
                        .anyMatch(m -> m.applicableTypes().contains(e.getKey()));
                if (!anywhere) result.add(e.getKey());
            }
            return result;
        }
    }
}
