package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.Announcement;
import com.portfolio.chungyak.domain.SpecialSupplyType;
import com.portfolio.chungyak.domain.UnitType;
import com.portfolio.chungyak.web.view.Prices;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 공고문 Q&A 의 <b>결정론 답변</b> (#39) — 답이 구조화 데이터에 이미 있는 질문은 LLM 을 부르지 않고 그 값으로 답한다.
 *
 * 순수 함수. 판정을 규칙 엔진이 하듯 Q&A 도 결정론을 앞세운다. 2026-10-13 실사용에서 틀린 답(당첨자 발표일을 공고일로,
 * 표의 다른 열을 월임대료로)은 대부분 이런 질문이었다.
 * <ul>
 *   <li>공고문 요약표(#38): 전매제한·재당첨제한·거주의무기간·분양가상한제·택지유형</li>
 *   <li>청약홈/LH API: 당첨자 발표일·청약 접수 일정·입주 예정·공급 세대수·특공 유형별 세대수·주택형별 최고 분양가</li>
 * </ul>
 * 의도는 키워드 규칙으로만 판단한다 — 주제어 + 질문 단서(언제·며칠·기간 …)가 함께 있어야 한다. 애매하면 답하지 않고
 * LLM 경로로 넘긴다(틀린 라우팅보다 낫다). 값이 없어도 넘긴다 — "모른다"고 단정하지 않는다.
 */
final class FactAnswerer {

    private FactAnswerer() {}

    /** @param source 화면에 보여줄 출처 */
    record Fact(String text, String source) {}

    private static final String SRC_TABLE = "공고문 첫 페이지 요약표";
    private static final String SRC_API = "청약홈·LH 공고 데이터";

    private static final Pattern WHEN = Pattern.compile("언제|며칠|몇일|날짜|날은|날이|일정|일은|일이|기간|시기|몇월|몇 월");
    /**
     * 이 단어가 있으면 일정·세대·가격 값을 묻는 질문이 아니다(자격·서류·방법, 무엇이 "포함"되는지) — LLM 경로로.
     * "발코니 확장비가 분양가에 포함돼 있나요?"에 분양가 목록으로 답하던 것을 막는다(#39 로컬 시험).
     */
    private static final Pattern NOT_FACT = Pattern.compile("자격|조건|서류|방법|대상|어떻게|절차|유의|주의|포함|별도|차이|비교");
    private static final Pattern MONEY_OTHER = Pattern.compile(
            "보증금|임대료|계약금|중도금|잔금|관리비|예치금|대출|발코니|확장|옵션|취득세|인지세|세금|부가세");
    /** 공급 유형·주택형을 콕 집은 세대수 질문 — 공고 전체 세대수로 답하면 틀린다(#39 eval "신혼부부 특별공급은 몇 세대") */
    private static final Pattern SUPPLY_PART = Pattern.compile(
            "특별공급|특공|일반공급|신혼|생애|다자녀|노부모|신생아|기관추천|청년|주택형|타입|평형|\\d+형|\\d+[a-z]");

    static Optional<Fact> answer(Announcement a, Optional<NoticeSummaryTable> table, String question) {
        if (a == null || question == null || question.isBlank()) return Optional.empty();
        String q = question.replaceAll("\\s+", "");

        // 1) 공고문 요약표 항목 — 이 단어들은 그 자체로 질문 대상이 분명하다(여러 개면 모두)
        if (table.isPresent()) {
            List<String> lines = new ArrayList<>();
            for (NoticeSummaryTable.Item it : table.get().items()) {
                if (asks(q, it.label())) lines.add(it.label() + ": " + it.value());
            }
            if (!lines.isEmpty()) return Optional.of(new Fact(String.join("\n", lines), SRC_TABLE));
        }
        if (NOT_FACT.matcher(q).find()) return Optional.empty();
        boolean when = WHEN.matcher(q).find();

        // 2) 당첨자 발표일
        if (q.contains("발표") && when && a.getWinnerAnnounceDate() != null) {
            return Optional.of(new Fact("당첨자 발표일은 " + date(a.getWinnerAnnounceDate()) + "입니다.", SRC_API));
        }
        // 3) 입주 예정
        // "입주자 모집공고일"·"입주지정기간"은 입주 예정 월과 다른 것을 묻는다
        if (q.contains("입주") && !q.contains("입주자") && !q.contains("지정")
                && (when || q.contains("예정")) && a.getMoveInYearMonth() != null
                && a.getMoveInYearMonth().matches("\\d{6}")) {
            String ym = a.getMoveInYearMonth();
            return Optional.of(new Fact("입주 예정은 " + ym.substring(0, 4) + "년 " + Integer.parseInt(ym.substring(4))
                    + "월입니다(공고 기준, 공정에 따라 바뀔 수 있음).", SRC_API));
        }
        // 4) 청약 접수 일정
        if ((q.contains("접수") || q.contains("청약신청") || q.contains("신청")) && when && a.getReceptBeginDate() != null) {
            StringBuilder sb = new StringBuilder();
            if (a.getSpecialReceptBeginDate() != null) {
                sb.append("특별공급 접수: ").append(range(a.getSpecialReceptBeginDate(), a.getSpecialReceptEndDate())).append('\n');
            }
            sb.append("청약 접수: ").append(range(a.getReceptBeginDate(), a.getReceptEndDate()));
            return Optional.of(new Fact(sb.toString(), SRC_API));
        }
        // 5a) 특별공급 유형별 세대수 — 청약홈이 주택형마다 주는 배정 세대(LH 는 유형 존재만 알아 넘긴다)
        Optional<Fact> byType = specialSupplyCount(a, q);
        if (byType.isPresent()) return byType;
        // 5) 공급 세대수
        if ((q.contains("몇세대") || q.contains("세대수") || q.contains("공급세대") || q.contains("총세대")
                || q.contains("몇가구")) && !SUPPLY_PART.matcher(q.toLowerCase()).find()
                && a.getTotalSupplyCount() != null && a.getTotalSupplyCount() > 0) {
            return Optional.of(new Fact("이 공고의 공급 세대는 총 " + String.format("%,d", a.getTotalSupplyCount()) + "세대입니다.", SRC_API));
        }
        // 6) 주택형별 최고 분양가 — 보증금·임대료·계약금 등 다른 금액 질문은 제외
        if ((q.contains("분양가") || q.contains("분양가격") || q.contains("공급가")) && !q.contains("상한")
                && !MONEY_OTHER.matcher(q).find()) {
            List<String> rows = new ArrayList<>();
            for (UnitType u : a.getUnitTypes()) {
                String price = Prices.formatManwon(u.getTopAmount());
                if (price != null) rows.add(u.getTypeName().strip() + ": " + price);
            }
            if (!rows.isEmpty()) {
                return Optional.of(new Fact("주택형별 최고 분양가(층·향과 무관한 최고 공급금액)\n" + String.join("\n", rows), SRC_API));
            }
        }
        return Optional.empty();
    }

    /** 질문의 특공 유형 핵심어 → 유형. 신혼희망타운은 청약홈이 유형별 세대수를 주지 않아(CLAUDE.md) 넣지 않는다 */
    private static final Map<String, SpecialSupplyType> TYPE_WORDS = Map.of(
            "신혼부부", SpecialSupplyType.NEWLYWED, "생애최초", SpecialSupplyType.FIRST_TIME,
            "다자녀", SpecialSupplyType.MULTI_CHILD, "노부모", SpecialSupplyType.OLD_PARENTS,
            "기관추천", SpecialSupplyType.INSTITUTION_RECOMMEND, "청년", SpecialSupplyType.YOUTH,
            "신생아", SpecialSupplyType.NEWBORN);
    private static final Pattern COUNT_ASK = Pattern.compile("몇세대|세대수|몇가구|가구수|물량|배정|몇개|몇채");

    /**
     * "신혼부부 특별공급은 몇 세대예요?" — 주택형별 배정 세대를 더한다(#39 eval 에서 AI 는 "1세대 1주택 기준"이라고 답했다).
     * 세대수를 모르는 공고(LH: 유형 존재만)·합계 0 이면 넘긴다 — 0 이 "배정 없음"인지 "값 없음"인지 단정하지 않는다.
     */
    private static Optional<Fact> specialSupplyCount(Announcement a, String q) {
        if (!COUNT_ASK.matcher(q).find() || q.contains("신혼희망") || a.getUnitTypes().isEmpty()) return Optional.empty();
        List<SpecialSupplyType> asked = TYPE_WORDS.entrySet().stream()
                .filter(e -> q.contains(e.getKey())).map(Map.Entry::getValue).sorted().toList();
        if (asked.isEmpty()) return Optional.empty();
        if (a.getUnitTypes().stream().anyMatch(u -> u.getSupplyBreakdown() == null || !u.getSupplyBreakdown().isCountsKnown())) {
            return Optional.empty();
        }
        List<String> lines = new ArrayList<>();
        for (SpecialSupplyType type : asked) {
            int total = 0;
            List<String> parts = new ArrayList<>();
            for (UnitType u : a.getUnitTypes()) {
                int n = u.getSupplyBreakdown().countOf(type);
                if (n <= 0) continue;
                total += n;
                parts.add(u.getTypeName().strip() + " " + n);
            }
            if (total == 0) return Optional.empty();
            lines.add(type.getLabel() + " 특별공급: 총 " + String.format("%,d", total) + "세대 (" + String.join(" · ", parts) + ")");
        }
        return Optional.of(new Fact(String.join("\n", lines), SRC_API));
    }

    /** 요약표 항목을 묻는가 — "전매제한"·"전매 기간"·"거주의무" 처럼 항목 이름의 핵심어로 */
    private static boolean asks(String q, String label) {
        return switch (label) {
            case "전매제한" -> q.contains("전매");
            case "재당첨제한" -> q.contains("재당첨");
            case "거주의무기간" -> q.contains("거주의무");
            case "분양가상한제" -> q.contains("분양가상한") || q.contains("상한제");
            case "택지유형" -> q.contains("택지");
            default -> false;
        };
    }

    private static String range(LocalDate from, LocalDate to) {
        if (to == null || to.equals(from)) return date(from);
        return date(from) + " ~ " + date(to);
    }

    private static String date(LocalDate d) {
        return d.getYear() + "년 " + d.getMonthValue() + "월 " + d.getDayOfMonth() + "일("
                + d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }
}
