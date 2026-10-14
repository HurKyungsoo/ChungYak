package com.portfolio.chungyak.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고문 <b>표 값</b> 결정론 답변 (#42) — 표를 행 단위로 편 원문({@code TableAwareTextStripper}: "열 이름: 값 · …")에서
 * 자주 묻는 표 값을 찾아 <b>행을 그대로</b> 보여 준다. LLM 을 거치지 않는다.
 *
 * 2026-10-13 실사용 오답이 표 값이었다(2901 월임대료 292,150원 → 6,600,000원). 원칙은 {@link FactAnswerer} 와 같다 —
 * 열 이름이 분명할 때만 답하고, 애매하면 답하지 않고 LLM 경로로 넘긴다(틀린 결정론 답은 LLM 오답보다 나쁘다).
 * <ul>
 *   <li>계약금 비율 — 분양 납부표의 "계약금 10%"·"계약금(5%)" 열 이름. 비율이 둘 이상이면 넘긴다.</li>
 *   <li>임대보증금·월임대료(임대 계약금 포함) — "월 임대료" 열이 조건 없이 있는 행만. "최대전환 시 월임대료"만 있는 표는
 *       기본 임대료가 이름 없는 칸에 있을 수 있어 넘긴다(공고 3000).</li>
 *   <li>소득 기준 — 열 이름이 "3인 이하·4인 …"인 표의 행.</li>
 * </ul>
 * 예전 형식 원문(위치순 추출)에는 "이름: 값" 행이 없어 저절로 답하지 않는다.
 */
final class TableFactAnswerer {

    private TableFactAnswerer() {}

    /** 화면에 "공고문 표 값 그대로 — AI 를 거치지 않았습니다"로 붙는다 */
    static final String SOURCE = "공고문 표";
    /** 한 번에 보여 줄 최대 행 — 넘치면 "외 N행" */
    static final int MAX_ROWS = 6;

    /** 금액을 묻는 단서 — "어떻게 정해져요"·"줄일 수 있나요" 같은 방법·가능 질문은 답하지 않는다 */
    private static final Pattern AMOUNT_ASK = Pattern.compile("얼마|금액|몇\\s*원|몇\\s*프로|몇\\s*퍼|퍼센트|비율|%|기준|한도|상한");
    private static final Pattern NOT_TABLE = Pattern.compile("어떻게|방법|절차|조건|서류|자격|가능|되나|돼요\\?|줄일|올리|전환|포함|별도");
    private static final Pattern SALE_EXCLUDE = Pattern.compile("발코니|확장|옵션|중도금|잔금|대출|이자");
    private static final Pattern RENT_WORD = Pattern.compile("임대료|월세|보증금|임대조건");
    /** 조건이 붙은 임대료 열 — 기본 임대 조건이 아니다 */
    private static final Pattern QUALIFIED = Pattern.compile("전환|증액|감액|최대|한도|할증");
    private static final Pattern MONEY = Pattern.compile("^[-+(]?[\\d,]{3,}\\s*(원|천원|만원)?(\\s*이하)?$");
    private static final Pattern DOWN_PCT = Pattern.compile("계약금\\s*\\(?\\s*(\\d{1,2})\\s*%");
    private static final Pattern PERSONS = Pattern.compile("^\\d인(\\s*이하|\\s*이상)?$");

    /** "이름: 값 · 이름: 값" 한 줄 */
    record Row(String line, List<Part> parts) {}

    record Part(String label, String value) {}

    static Optional<FactAnswerer.Fact> answer(String documentText, String question) {
        if (documentText == null || question == null || question.isBlank()) return Optional.empty();
        String q = question.strip();
        if (!AMOUNT_ASK.matcher(q).find() || NOT_TABLE.matcher(q).find()) return Optional.empty();
        List<Row> rows = rows(documentText);
        if (rows.isEmpty()) return Optional.empty();

        boolean down = q.contains("계약금");
        boolean rent = RENT_WORD.matcher(q).find();
        boolean income = q.contains("소득") && !q.contains("자산");

        if (down && !rent && !SALE_EXCLUDE.matcher(q).find()) {
            Optional<FactAnswerer.Fact> pct = downPaymentPercent(rows);
            if (pct.isPresent()) return pct;
        }
        if (rent || down) {
            Optional<FactAnswerer.Fact> rental = rentalRows(rows);
            if (rental.isPresent()) return rental;
        }
        if (income) return incomeRows(rows);
        return Optional.empty();
    }

    static List<Row> rows(String text) {
        List<Row> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            if (!line.contains(" · ") || !line.contains(": ")) continue;
            List<Part> parts = new ArrayList<>();
            for (String p : line.split(" · ")) {
                int i = p.indexOf(": ");
                parts.add(i < 0 ? new Part("", p.strip()) : new Part(p.substring(0, i).strip(), p.substring(i + 2).strip()));
            }
            out.add(new Row(line.strip(), parts));
        }
        return out;
    }

    /** 분양 납부표 — 계약금 열 이름의 비율. 발코니·옵션 표는 뺀다. 비율이 하나일 때만. */
    private static Optional<FactAnswerer.Fact> downPaymentPercent(List<Row> rows) {
        Set<Integer> percents = new TreeSet<>();
        Row example = null;
        for (Row r : rows) {
            // 발코니 확장·옵션 납부표도 "계약금(10%)" 열을 쓴다 — 분양 납부표만 본다
            if (r.line().contains("발코니") || r.line().contains("확장") || r.line().contains("옵션")) continue;
            for (Part p : r.parts()) {
                Matcher m = DOWN_PCT.matcher(p.label());
                if (!m.find()) continue;
                percents.add(Integer.parseInt(m.group(1)));
                if (example == null && MONEY.matcher(p.value()).matches()) example = r;
            }
        }
        if (percents.size() != 1 || example == null) return Optional.empty();
        int pct = percents.iterator().next();
        return Optional.of(new FactAnswerer.Fact(
                "계약금은 공급금액의 " + pct + "%입니다. 금액은 주택형·층마다 다릅니다 — 공고문 표의 첫 행:\n- " + example.line(),
                SOURCE));
    }

    /** 임대 조건 표 — 조건 없는 "임대료" 열이 금액인 행 */
    private static Optional<FactAnswerer.Fact> rentalRows(List<Row> rows) {
        List<String> hits = new ArrayList<>();
        for (Row r : rows) {
            boolean baseRent = r.parts().stream().anyMatch(p -> p.label().contains("임대료")
                    && !QUALIFIED.matcher(p.label()).find() && MONEY.matcher(p.value()).matches());
            if (baseRent) hits.add(r.line());
        }
        return listing(hits, "임대보증금·월임대료(주택형별, 공고문 표 그대로):");
    }

    /** 소득 기준 표 — 열 이름이 가구원 수(3인 이하·4인 …)인 행 */
    private static Optional<FactAnswerer.Fact> incomeRows(List<Row> rows) {
        List<String> hits = new ArrayList<>();
        for (Row r : rows) {
            long personCols = r.parts().stream()
                    .filter(p -> PERSONS.matcher(p.label()).matches() && MONEY.matcher(p.value()).matches()).count();
            if (personCols >= 2 && r.line().contains("소득")) hits.add(r.line());
        }
        return listing(hits, "소득 기준(가구원 수별, 공고문 표 그대로):");
    }

    private static Optional<FactAnswerer.Fact> listing(List<String> lines, String head) {
        Set<String> distinct = new LinkedHashSet<>(lines);
        if (distinct.isEmpty()) return Optional.empty();
        StringBuilder sb = new StringBuilder(head);
        int n = 0;
        for (String l : distinct) {
            if (n++ == MAX_ROWS) break;
            sb.append("\n- ").append(l);
        }
        if (distinct.size() > MAX_ROWS) sb.append("\n(외 ").append(distinct.size() - MAX_ROWS).append("행 — 공고문 원문을 확인하세요)");
        return Optional.of(new FactAnswerer.Fact(sb.toString(), SOURCE));
    }
}
