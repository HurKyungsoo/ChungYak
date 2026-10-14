package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.SupplyBreakdown;
import com.portfolio.chungyak.domain.SupplyCategory;
import com.portfolio.chungyak.domain.UnitType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "이 공고에 물어보기" 아래 <b>자주 묻는 질문 버튼</b> (#40 1단계) — 결정론.
 *
 * <ul>
 *   <li>공식 데이터 버튼: 후보 문장을 {@link FactAnswerer} 에 실제로 넣어 보고 <b>답이 나오는 것만</b> 보여준다 —
 *       누르면 반드시 FACT 경로(AI 비용 0·오답 여지 없음). 값이 없는 공고에는 버튼이 생기지 않는다.</li>
 *   <li>AI 버튼: 결정론으로 못 답하는 질문(계약금 비율·견본주택 위치·자격 등). 문구는 검색 eval
 *       ({@code eval/notice-qa-cases.tsv})에 있는 질문 그대로라 검색이 정답 발췌를 찾는지 매 빌드에서 잰다.</li>
 * </ul>
 * 공고 종류(청약홈 분양 / LH 임대 / 무순위·잔여)마다 묻는 게 달라 후보를 나눈다.
 */
public final class QuickQuestions {

    private QuickQuestions() {}

    /** @param fact true 면 구조화 데이터로 답한다(화면에 "공식 데이터" 표시) */
    public record QuickQuestion(String text, boolean fact) {}

    static final int MAX_FACT = 5;

    // 공식 데이터 후보 — FactAnswerer 키워드 규칙에 맞춘 문장(바꾸면 QuickQuestionsTest 가 경로를 확인한다)
    static final String WINNER = "당첨자 발표일은 언제예요?";
    static final String RECEPT = "청약 접수 일정은 언제예요?";
    static final String RESALE = "전매제한 기간은?";
    static final String RESIDE = "거주의무기간은?";
    static final String PRICE = "분양가는 얼마예요?";
    static final String MOVE_IN = "입주 예정은 언제예요?";
    static final String TOTAL = "공급 세대수는?";

    // AI 후보 — eval/notice-qa-cases.tsv 의 질문과 글자까지 같게 둔다
    static final String DEPOSIT = "계약금은 몇 퍼센트예요?";
    static final String MODEL_HOUSE = "견본주택 주소가 어디예요?";
    static final String QUALIFY = "신청 자격이 뭐예요?";
    static final String DOCS_WHEN = "서류 제출 기간은 언제예요?";
    static final String WAITLIST = "예비입주자는 어떻게 뽑나요?";

    public static List<QuickQuestion> of(Announcement a, Optional<NoticeSummaryTable> table) {
        if (a == null) return List.of();
        List<String> factCandidates = new ArrayList<>();
        List<String> ai = new ArrayList<>();

        if (a.isLh() && a.getSupplyCategory() == SupplyCategory.RENTAL) {
            factCandidates.addAll(List.of(RECEPT, WINNER, TOTAL, MOVE_IN));
            ai.add(QUALIFY);
            ai.add(a.isWaitlist() ? WAITLIST : DOCS_WHEN);
        } else if (a.isRemainder()) {
            factCandidates.addAll(List.of(RECEPT, WINNER, PRICE, RESALE, RESIDE));
            ai.addAll(List.of(QUALIFY, DEPOSIT));
        } else {
            factCandidates.addAll(List.of(WINNER, RECEPT, RESALE, RESIDE));
            topSpecialTypes(a, 2).forEach(t -> factCandidates.add(t.getLabel() + " 특별공급은 몇 세대예요?"));
            factCandidates.addAll(List.of(PRICE, MOVE_IN));
            ai.addAll(List.of(DEPOSIT, MODEL_HOUSE));
        }

        List<QuickQuestion> out = new ArrayList<>();
        for (String q : factCandidates) {
            if (out.size() >= MAX_FACT) break;
            if (FactAnswerer.answer(a, table, q).isPresent()) out.add(new QuickQuestion(q, true));
        }
        ai.forEach(q -> out.add(new QuickQuestion(q, false)));
        return out;
    }

    /** 버튼 문장에 라벨을 그대로 쓴다 — FactAnswerer 핵심어가 라벨에 들어 있는 유형만("다자녀가구"⊃"다자녀") */
    private static final java.util.Set<SpecialSupplyType> ASKABLE = java.util.EnumSet.of(
            SpecialSupplyType.NEWLYWED, SpecialSupplyType.FIRST_TIME, SpecialSupplyType.MULTI_CHILD,
            SpecialSupplyType.OLD_PARENTS, SpecialSupplyType.NEWBORN, SpecialSupplyType.YOUTH);

    /** 배정 세대가 많은 특공 유형 — 세대수를 모르는 주택형이 하나라도 있으면 없음(FactAnswerer 와 같은 기준) */
    private static List<SpecialSupplyType> topSpecialTypes(Announcement a, int n) {
        Map<SpecialSupplyType, Integer> totals = new EnumMap<>(SpecialSupplyType.class);
        for (UnitType u : a.getUnitTypes()) {
            SupplyBreakdown b = u.getSupplyBreakdown();
            if (b == null || !b.isCountsKnown()) return List.of();
            for (SpecialSupplyType t : ASKABLE) {
                int c = b.countOf(t);
                if (c > 0) totals.merge(t, c, Integer::sum);
            }
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<SpecialSupplyType, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .limit(n)
                .toList();
    }
}
