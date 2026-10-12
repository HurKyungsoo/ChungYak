package com.portfolio.chungyak.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 추출 결과를 사용자가 눈으로 검증하게 돕는다 — "이렇게 읽었어요" 목록과 원문 숫자 대조 경고.
 *
 * 순수 함수다(LLM 호출 없음). 판정에 쓰이는 값을 코드가 바꾸지는 않는다 — 모델이 "3년"을 24개월로 읽는 식의
 * 숫자 오독은 프롬프트로 막기 어렵고, 그 값이 곧 판정 입력이라 <b>보여 주고 경고할 뿐</b> 사용자가 확인·수정하게 한다
 * (CLAUDE.md 절대 규칙 2: 추출값은 사용자가 폼에서 확인한 뒤에만 판정으로 들어간다).
 */
final class ExtractionReadBack {

    private static final Pattern YEARS_MONTHS = Pattern.compile("(\\d+)\\s*년(?:\\s*(\\d+)\\s*개월)?");
    private static final Pattern YEARS_HALF = Pattern.compile("(\\d+)\\s*년\\s*반");
    private static final Pattern NTH_YEAR = Pattern.compile("(\\d+)\\s*년\\s*차");
    private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*개월");

    // 한글 수사 — "두 해", "오 년", "여섯 달". 고유어(한·두·세…)는 해/년/달, 한자어(일·이·삼…)는 년/개월에만 붙는다
    // ("이해해요" 의 "이해" 를 2년으로 읽지 않도록 한자어+해 는 제외). 1~10 까지만 — 더 큰 수는 숫자로 쓰는 게 보통이다.
    private static final String NATIVE = "한|두|세|네|다섯|여섯|일곱|여덟|아홉|열";
    private static final String SINO = "일|이|삼|사|오|육|칠|팔|구|십";
    private static final Pattern NATIVE_YEAR = Pattern.compile("(" + NATIVE + ")\\s*(?:해|년)");
    private static final Pattern SINO_YEAR = Pattern.compile("(" + SINO + ")\\s*년");
    private static final Pattern NATIVE_MONTH = Pattern.compile("(" + NATIVE + ")\\s*달");
    private static final Pattern SINO_MONTH = Pattern.compile("(" + SINO + ")\\s*개월");

    private ExtractionReadBack() {}

    /** 개월 수를 "24개월(2년)", "30개월(2년 6개월)", "6개월" 로 — 년·개월 어느 쪽으로 적었든 바로 비교되게. */
    static String months(int m) {
        if (m < 12) return m + "개월";
        int y = m / 12, r = m % 12;
        return m + "개월(" + y + "년" + (r == 0 ? "" : " " + r + "개월") + ")";
    }

    /** 폼에서 AI 가 채운 칸의 입력 이름 — 화면이 해당 칸에 "AI 입력" 표시를 붙인다. */
    static List<String> filledFields(ExtractedProfile p) {
        List<String> f = new ArrayList<>();
        if (p.married() != null) f.add("married");
        if (p.monthsSinceMarriage() != null) f.add("monthsSinceMarriage");
        if (p.childCount() != null) f.add("childCount");
        if (p.hasNewborn() != null) f.add("hasNewborn");
        if (p.houseless() != null) f.add("houseless");
        if (p.accountMonths() != null) f.add("accountMonths");
        if (p.everOwnedHouse() != null) f.add("everOwnedHouse");
        if (p.supportingOldParents() != null) f.add("supportingOldParents");
        if (p.householdHead() != null) f.add("householdHead");
        return f;
    }

    /** "이렇게 읽었어요" — 채워진 값만, 사람이 읽는 문장으로. */
    static List<String> readBack(ExtractedProfile p) {
        List<String> out = new ArrayList<>();
        if (p.married() != null) out.add(p.married() ? "혼인 중" : "혼인 중 아님");
        if (p.monthsSinceMarriage() != null) out.add("혼인 기간 " + months(p.monthsSinceMarriage()));
        if (p.childCount() != null) out.add("미성년 자녀 " + p.childCount() + "명");
        if (p.hasNewborn() != null) out.add(p.hasNewborn() ? "2세 이하 자녀 있음" : "2세 이하 자녀 없음");
        if (p.houseless() != null) out.add(p.houseless() ? "무주택" : "주택 소유 중");
        if (p.accountMonths() != null) out.add("청약통장 가입 " + months(p.accountMonths()));
        if (p.everOwnedHouse() != null) out.add(p.everOwnedHouse() ? "과거 주택 소유 이력 있음" : "과거 주택 소유 이력 없음");
        if (p.supportingOldParents() != null) out.add(p.supportingOldParents() ? "노부모 3년 이상 부양 중" : "노부모 부양 아님");
        if (p.householdHead() != null) out.add(p.householdHead() ? "세대주" : "세대원");
        return out;
    }

    /** 문장에 숫자로 적힌 기간을 개월로 — "3년"→36, "2년 6개월"→30, "3년 반"→42, "4년차"→48·36, "8개월"→8. */
    static Set<Integer> monthsIn(String text) {
        Set<Integer> found = new TreeSet<>();
        if (text == null) return found;
        Matcher m = YEARS_MONTHS.matcher(text);
        while (m.find()) {
            int total = Integer.parseInt(m.group(1)) * 12 + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
            found.add(total);
        }
        m = YEARS_HALF.matcher(text);
        while (m.find()) found.add(Integer.parseInt(m.group(1)) * 12 + 6);
        m = NTH_YEAR.matcher(text);   // N년차 = 만 N-1년 지난 N번째 해 — 둘 다 정당한 읽기다
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            found.add(n * 12);
            if (n > 0) found.add((n - 1) * 12);
        }
        m = MONTHS.matcher(text);
        while (m.find()) found.add(Integer.parseInt(m.group(1)));
        addKorean(found, NATIVE_YEAR, text, 12);
        addKorean(found, SINO_YEAR, text, 12);
        addKorean(found, NATIVE_MONTH, text, 1);
        addKorean(found, SINO_MONTH, text, 1);
        return found;
    }

    private static void addKorean(Set<Integer> found, Pattern p, String text, int unit) {
        Matcher m = p.matcher(text);
        while (m.find()) found.add(koreanNumber(m.group(1)) * unit);
    }

    private static int koreanNumber(String w) {
        return switch (w) {
            case "한", "일" -> 1;
            case "두", "이" -> 2;
            case "세", "삼" -> 3;
            case "네", "사" -> 4;
            case "다섯", "오" -> 5;
            case "여섯", "육" -> 6;
            case "일곱", "칠" -> 7;
            case "여덟", "팔" -> 8;
            case "아홉", "구" -> 9;
            default -> 10;   // 열·십
        };
    }

    /**
     * 모델이 채운 기간이 문장 속 숫자와 맞지 않으면 경고. 값을 고치거나 지우지 않는다.
     * - 문장에 기간 숫자가 있는데 어느 것과도 다르다 (예: "통장 3년" → 24개월)
     * - 문장에 기간 숫자가 아예 없는데 값이 채워졌다 (예: "재작년에 결혼" → 24개월)
     */
    static List<String> warnings(String text, ExtractedProfile p) {
        List<String> out = new ArrayList<>();
        Set<Integer> inText = monthsIn(text);
        check(out, "혼인 기간", p.monthsSinceMarriage(), inText);
        check(out, "청약통장 가입 기간", p.accountMonths(), inText);
        return out;
    }

    private static void check(List<String> out, String label, Integer value, Set<Integer> inText) {
        if (value == null || inText.contains(value)) return;
        if (inText.isEmpty()) {
            out.add("문장에 기간이 숫자로 적혀 있지 않은데 " + label + "이 " + months(value)
                    + "으로 채워졌어요. 근거가 없다면 직접 고쳐 주세요.");
        } else {
            List<String> written = new ArrayList<>();
            inText.forEach(m -> written.add(months(m)));
            out.add("문장에 적힌 기간은 " + String.join(", ", written) + "인데 " + label + "이 "
                    + months(value) + "으로 채워졌어요. 맞는지 확인해 주세요.");
        }
    }
}
