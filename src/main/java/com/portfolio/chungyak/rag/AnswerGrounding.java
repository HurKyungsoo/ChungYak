package com.portfolio.chungyak.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고문 Q&A 답변의 <b>금액·날짜</b>가 근거 발췌에 "그 항목의 값"으로 적혀 있는지 사후 검사 (#31).
 *
 * 순수 함수. 판정 요약의 ContradictionCheck 와 같은 자리다 — 프롬프트로는 못 막는 것을 결정론적으로 막는다.
 * 공고문 표는 텍스트로 뽑히면 열 구분이 사라진다({@code "임대료 059.9800A 33,148,000 6,600,000 …"}).
 * 숫자는 발췌에 실제로 있으므로 "지어낸 숫자" 검사로는 못 잡고, 모델은 다른 열 값을 월임대료로 단정했다
 * (#2901: 실제 292,150원 → 답 6,600,000원).
 *
 * <b>규칙.</b> 답변의 금액·날짜마다, 같은 문장에서 그 값을 가리킨 항목 이름(가장 가까운 것)을 찾고 —
 * <ul>
 *   <li>항목 이름이 있으면: 발췌에서 그 항목 이름 <b>바로 다음에 나오는 첫 숫자</b>({@link #WINDOW}자 안)가 같은 값이어야 한다.
 *       표 한 줄처럼 이름 뒤에 다른 숫자가 먼저 오면 그 값은 그 항목의 근거가 아니다.</li>
 *   <li>항목 이름이 없으면: 값이 발췌 어딘가에는 있어야 한다(지어낸 값 방지).</li>
 * </ul>
 * 대가: 표에만 있는 금액은 정답이어도 막힌다 — 틀린 금액을 단정하는 것보다 낫다(청약은 금액·날짜가 틀리면 피해가 크다).
 */
final class AnswerGrounding {

    private AnswerGrounding() {}

    /** 발췌에서 항목 이름 뒤로 값을 찾는 범위(글자) */
    static final int WINDOW = 30;
    /** 항목 이름 앞으로 날짜를 찾는 범위(글자) — 기간 하나("2026.11.02.(월)~11.06(금)까지 등기우편으로 ")가 들어갈 만큼 */
    static final int BACK_WINDOW = 45;

    /** 금액 — "1억 2,000만원", "1,000만원", "33,148,000원", "292,150" (쉼표 묶음은 원 없이도 금액으로 본다) */
    private static final Pattern MONEY = Pattern.compile(
            "(\\d[\\d,]*)\\s*억(?:\\s*(\\d[\\d,]*)\\s*만)?\\s*원?|(\\d[\\d,]*)\\s*만\\s*원|(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원|(\\d{1,3}(?:,\\d{3})+)");

    /** 날짜 — "2026년 10월 26일", "2026.10.26", "10월 26일", "10.26(월)", "2017년 7월" */
    private static final Pattern DATE = Pattern.compile(
            "(?:(\\d{4})\\s*[년.\\-/]\\s*)?(\\d{1,2})\\s*[월.\\-/]\\s*(?:(\\d{1,2})\\s*일?)?");

    /** 발췌에서 "다음 숫자"를 찾을 때 쓰는 아무 숫자 토큰(주택형 059.9800A 같은 것도 숫자로 본다) */
    private static final Pattern ANY_NUMBER = Pattern.compile("\\d[\\d,.]*");

    /**
     * 문장 끝(마침표·물음표 뒤 공백)으로만 나눈다 — 줄바꿈은 공백으로 바꿔 문장 중간 줄바꿈이 항목 이름과 값을
     * 갈라놓지 않게 한다. "2026.10.26." 의 마침표 뒤에는 공백이 없어 날짜가 쪼개지지 않는다.
     */
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?。])\\s+");

    /**
     * 유니코드 공백 전부 → 일반 공백. 모델(gpt-oss)은 숫자와 단위 사이에 좁은 줄바꿈 없는 공백(U+202F)을 쓰는데
     * ("2027 년 7 월"), 자바 정규식 {@code \s} 는 이를 공백으로 보지 않아 날짜가 아예 인식되지 않고 검사를 통과했다
     * (#31 실사용 — 틀린 입주 시기가 그대로 나갔다).
     */
    private static final Pattern ANY_SPACE = Pattern.compile("[\\p{Z}\\s]+");

    /** 답변의 항목 이름 → 발췌에서 찾을 이름들. 긴 이름을 앞에(“월임대료”가 “임대료”보다 먼저). */
    private static final Map<String, List<String>> MONEY_LABELS = labels(
            "임대보증금", List.of("임대보증금", "보증금"),
            "전환보증금", List.of("전환보증금"),
            "보증금", List.of("임대보증금", "보증금"),
            "월임대료", List.of("월임대료", "월 임대료", "임대료"),
            "월 임대료", List.of("월임대료", "월 임대료", "임대료"),
            "임대료", List.of("월임대료", "월 임대료", "임대료"),
            "계약금", List.of("계약금"),
            "중도금", List.of("중도금"),
            "잔금", List.of("잔금"),
            "분양가", List.of("분양가", "분양가격", "공급금액", "공급가격"),
            "공급금액", List.of("공급금액", "분양가", "공급가격"),
            "관리비", List.of("관리비"),
            "예치금", List.of("예치금"));

    private static final Map<String, List<String>> DATE_LABELS = labels(
            "당첨자 발표", List.of("당첨자 발표", "당첨자발표", "발표"),
            "발표", List.of("발표"),
            "입주", List.of("입주"),
            "접수", List.of("접수", "신청"),
            "신청", List.of("신청", "접수"),
            "계약", List.of("계약"),
            "서류", List.of("서류"),
            "공고일", List.of("공고일", "공고"),
            "종료", List.of("종료"));

    /**
     * @return 근거가 없는 금액·날짜가 있으면 그 설명, 없으면 empty
     */
    static Optional<String> check(String answer, List<String> excerpts) {
        if (answer == null || answer.isBlank()) return Optional.empty();
        String corpus = ANY_SPACE.matcher(String.join("\n", excerpts)).replaceAll(" ");
        for (String sentence : SENTENCE.split(ANY_SPACE.matcher(answer).replaceAll(" "))) {
            Optional<String> p = checkMoney(sentence, corpus);
            if (p.isPresent()) return p;
            p = checkDates(sentence, corpus);
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }

    // ── 금액 ──────────────────────────────────────────────────────────────────

    private static Optional<String> checkMoney(String sentence, String corpus) {
        Matcher m = MONEY.matcher(sentence);
        while (m.find()) {
            long value = money(m);
            if (value <= 0) continue;
            String label = nearestLabel(sentence, m.start(), m.end(), MONEY_LABELS);
            if (label == null) {
                if (!moneyAnywhere(corpus, value)) return Optional.of("금액 " + m.group().strip() + " 이(가) 발췌에 없음");
            } else if (!labelFollowedBy(corpus, MONEY_LABELS.get(label), next -> moneyAt(corpus, next) == value)) {
                return Optional.of("'" + label + "' " + m.group().strip() + " — 발췌에서 그 항목 바로 뒤의 값이 아님");
            }
        }
        return Optional.empty();
    }

    /** 매처 위치의 금액을 원 단위로 */
    private static long money(Matcher m) {
        try {
            if (m.group(1) != null) {
                long eok = num(m.group(1)) * 100_000_000L;
                return eok + (m.group(2) != null ? num(m.group(2)) * 10_000L : 0);
            }
            if (m.group(3) != null) return num(m.group(3)) * 10_000L;
            if (m.group(4) != null) return num(m.group(4));
            return num(m.group(5));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** corpus 의 from 위치에서 시작하는 금액 — 금액 형식이 아니면 -1 */
    private static long moneyAt(String corpus, int from) {
        Matcher m = MONEY.matcher(corpus);
        m.region(from, corpus.length());
        return m.lookingAt() ? money(m) : -1;
    }

    private static boolean moneyAnywhere(String corpus, long value) {
        Matcher m = MONEY.matcher(corpus);
        while (m.find()) if (money(m) == value) return true;
        return false;
    }

    // ── 날짜 ──────────────────────────────────────────────────────────────────

    private record Ymd(Integer y, int m, Integer d) {
        boolean sameAs(Ymd o) {
            if (m != o.m) return false;
            if (y != null && o.y != null && !y.equals(o.y)) return false;
            return d == null || o.d == null || d.equals(o.d);
        }
    }

    private static Optional<String> checkDates(String sentence, String corpus) {
        Matcher m = DATE.matcher(sentence);
        while (m.find()) {
            Ymd date = ymd(m);
            if (date == null) continue;
            String label = nearestLabel(sentence, m.start(), m.end(), DATE_LABELS);
            if (label == null) {
                if (!dateAnywhere(corpus, date)) return Optional.of("날짜 " + m.group().strip() + " 이(가) 발췌에 없음");
            } else if (!labelFollowedBy(corpus, DATE_LABELS.get(label), next -> rangeAt(corpus, next).stream().anyMatch(date::sameAs))
                    && !labelPrecededBy(corpus, DATE_LABELS.get(label), date)) {
                return Optional.of("'" + label + "' " + m.group().strip() + " — 발췌에서 그 항목 바로 뒤의 날짜가 아님");
            }
        }
        return Optional.empty();
    }

    /** 월만 있고 연도·일이 다 없으면(“3개월”의 3 같은 것) 날짜로 보지 않는다. 월·일 범위도 본다. */
    private static Ymd ymd(Matcher m) {
        if (m.group(1) == null && m.group(3) == null) return null;
        int month = Integer.parseInt(m.group(2));
        Integer day = m.group(3) == null ? null : Integer.parseInt(m.group(3));
        if (month < 1 || month > 12 || (day != null && (day < 1 || day > 31))) return null;
        return new Ymd(m.group(1) == null ? null : Integer.parseInt(m.group(1)), month, day);
    }

    /** 기간 이음표 — "11.02(월) ~ 11.06(금)", "11.02부터 11.06까지" */
    private static final Pattern RANGE_JOIN = Pattern.compile("^\\.?\\s*(?:\\([^)]{1,3}\\))?\\.?\\s*(?:~|∼|-|부터)\\s*$");

    /**
     * 날짜와 그 뒤 항목 이름 사이 — 날짜에 "까지·부터·에" 가 붙어 같은 문장으로 이어져야 한다. "[임대종료일 2027.07.31.]
     * 기존 단지 최초 입주" 처럼 괄호·기호로 끊긴 앞 날짜는 그 항목의 값이 아니다.
     */
    private static final Pattern DATE_THEN_LABEL =
            Pattern.compile("^\\.?(?:\\([^)]{1,3}\\))?\\.?\\s*(?:까지|부터|에|이후|이전)[^\\[\\]:：■※]{0,20}$");

    /** from 위치의 날짜, 그리고 바로 뒤가 기간(~)이면 그 끝 날짜까지 — 날짜가 아니면 빈 목록 */
    private static List<Ymd> rangeAt(String corpus, int from) {
        Matcher m = DATE.matcher(corpus);
        m.region(from, corpus.length());
        if (!m.lookingAt()) return List.of();
        Ymd first = ymd(m);
        List<Ymd> out = new ArrayList<>();
        if (first != null) out.add(first);
        int end = m.end();
        Matcher second = DATE.matcher(corpus);
        second.region(end, Math.min(corpus.length(), end + 12));
        if (second.find() && RANGE_JOIN.matcher(corpus.substring(end, second.start())).matches()) {
            Ymd d = ymd(second);
            if (d != null) out.add(d);
        }
        return out;
    }

    /**
     * 한국어 문장은 날짜가 항목 앞에 오기도 한다 — "2026.11.02.(월)~11.06(금)까지 등기우편으로 서류를 제출" (#39 로컬 시험,
     * 정답이 숨겨졌다). 항목 이름 바로 앞 {@link #BACK_WINDOW}자 안의 <b>마지막</b> 날짜(기간이면 그 시작 날짜도)가 같으면 근거로 본다.
     * 날짜만 — 금액은 표의 "값 값 항목" 줄에서 앞 값을 잘못 짚기 쉬워 뒤 방향만 본다.
     */
    private static boolean labelPrecededBy(String corpus, List<String> names, Ymd date) {
        for (String name : names) {
            int i = corpus.indexOf(name);
            while (i >= 0) {
                // 기간("2026.11.02.(월)~11.06(금)까지 …")의 시작 날짜까지 닿게 조금 넓게, 숫자 중간에서 시작하지 않게
                int from = Math.max(0, i - BACK_WINDOW);
                while (from > 0 && (Character.isDigit(corpus.charAt(from - 1)) || corpus.charAt(from - 1) == '.')) from--;
                Matcher m = DATE.matcher(corpus);
                m.region(from, i);
                Ymd last = null, prev = null;
                int lastStart = -1, lastEnd = -1, prevEnd = -1;
                while (m.find()) {
                    Ymd d = ymd(m);
                    if (d == null) continue;
                    prev = last;
                    prevEnd = lastEnd;
                    last = d;
                    lastStart = m.start();
                    lastEnd = m.end();
                }
                boolean bound = last != null && DATE_THEN_LABEL.matcher(corpus.substring(lastEnd, i)).matches();
                if (bound && last.sameAs(date)) return true;
                if (bound && prev != null && RANGE_JOIN.matcher(corpus.substring(prevEnd, lastStart)).matches()
                        && prev.sameAs(date)) {
                    return true;
                }
                i = corpus.indexOf(name, i + 1);
            }
        }
        return false;
    }

    private static boolean dateAnywhere(String corpus, Ymd date) {
        Matcher m = DATE.matcher(corpus);
        while (m.find()) {
            Ymd d = ymd(m);
            if (d != null && d.sameAs(date)) return true;
        }
        return false;
    }

    // ── 공통 ──────────────────────────────────────────────────────────────────

    /** 같은 문장에서 값과 가장 가까운 항목 이름(공백 무시 비교) — 없으면 null */
    private static String nearestLabel(String sentence, int start, int end, Map<String, List<String>> labels) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String label : labels.keySet()) {
            int i = sentence.indexOf(label);
            while (i >= 0) {
                int dist = i + label.length() <= start ? start - (i + label.length()) : i >= end ? i - end : 0;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = label;
                }
                i = sentence.indexOf(label, i + 1);
            }
        }
        return best;
    }

    /** 발췌의 항목 이름 중 하나라도, 그 바로 뒤 첫 숫자({@link #WINDOW}자 안)가 조건을 만족하는가 */
    private static boolean labelFollowedBy(String corpus, List<String> names,
                                           java.util.function.IntPredicate valueStartsAt) {
        for (String name : names) {
            int i = corpus.indexOf(name);
            while (i >= 0) {
                int after = i + name.length();
                Matcher n = ANY_NUMBER.matcher(corpus);
                n.region(after, Math.min(corpus.length(), after + WINDOW));
                if (n.find() && valueStartsAt.test(n.start())) return true;
                i = corpus.indexOf(name, i + 1);
            }
        }
        return false;
    }

    private static long num(String s) {
        return Long.parseLong(s.replace(",", ""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> labels(Object... kv) {
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], (List<String>) kv[i + 1]);
        return out;
    }
}
