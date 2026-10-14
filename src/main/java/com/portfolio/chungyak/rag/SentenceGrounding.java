package com.portfolio.chungyak.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고문 Q&A 답변의 <b>서술 문장</b>이 근거 발췌에 기대고 있는지 사후 검사 (#41, #31 후속).
 *
 * 순수 함수. {@link AnswerGrounding} 은 금액·날짜만 본다 — 그 밖의 문장("대학생 계층에 해당하는 경우에는 …
 * 신청자격이 인정됩니다")은 발췌에 없어도 그대로 나갔다. LLM 으로 LLM 을 검사하지 않는다(판정 경계와 같은 원칙).
 *
 * 문장마다 두 가지를 본다:
 * <ol>
 *   <li>숫자 + 단위(년·개월·세·%·회·세대·인·층) — 발췌에 같은 값이 있어야 한다. "혼인 7년 이내"를 "10년 이내"로 쓴 답.</li>
 *   <li>핵심어 겹침 — 문장의 내용어(조사·어미를 뗀 2글자 이상, 공고문 어디에나 있는 말은 뺀다) 중
 *       {@link #MIN_COVERAGE} 이상이 발췌에 있어야 한다. 내용어가 {@link #MIN_WORDS} 개 미만인 짧은 문장은 보지 않는다.</li>
 * </ol>
 * <b>못 잡는 것</b>(오프라인 eval 로 확인): 발췌 문구를 엉뚱하게 이어 붙인 문장, 부정을 뒤집은 문장("포함되어 있지 않다"
 * → "포함되어 있다"). 낱말은 발췌에 다 있기 때문이다. 이 검사는 지어낸 내용·바뀐 숫자를 잡는 그물이다.
 *
 * 걸린 문장은 지우지 않는다 — 화면이 "발췌에서 확인되지 않은 문장"으로 표시하고 원문 확인을 권한다(문장을 빼면 답의 뜻이
 * 바뀔 수 있다). 금액·날짜가 어긋난 답은 여전히 {@link AnswerGrounding} 이 통째로 숨긴다.
 */
final class SentenceGrounding {

    private SentenceGrounding() {}

    /** 핵심어 중 발췌에 있어야 하는 비율 — 오프라인 판정 세트(23문장)로 정했다 */
    static final double MIN_COVERAGE = 0.6;
    /** 이보다 내용어가 적은 문장은 겹침으로 판단하지 않는다("외국인은 신청할 수 없습니다") */
    static final int MIN_WORDS = 3;

    private static final Pattern CITATION = Pattern.compile("\\[\\d+]");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?。])\\s+");
    /** 조각 앞머리의 인용 번호 — "[1][2] 다음 문장" 의 [1][2] 는 앞 문장 것이다 */
    private static final Pattern LEADING_CITATIONS = Pattern.compile("^((?:\\[\\d+]\\s*)+)");
    private static final Pattern ANY_SPACE = Pattern.compile("[\\p{Z}\\s]+");
    private static final Pattern HANGUL_WORD = Pattern.compile("[가-힣]{2,}");
    /** 숫자 + 단위 — 금액(원)·날짜는 {@link AnswerGrounding} 몫이라 뺀다 */
    private static final Pattern NUMBER_UNIT =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(개월|년|세|%|퍼센트|회|세대|인|층)(?![가-힣]{0,1}\\d)");
    /** 안내 문장 — 근거를 말하는 문장이 아니다 */
    private static final Pattern GUIDANCE = Pattern.compile("원문|확인하세요|확인하시기|확인되지 않습니다|문의하세요|문의하시기");
    /**
     * "발췌에서는 금액이 명확히 표시되지 않습니다" — 못 찾았다는 말이지 주장이 아니다. 이런 답에 "원문 확인 필요"를 붙이면
     * 운영에서 본 대로(2026-10-14, 공고 2901) 경고가 답과 같은 말을 반복한다. 발췌·공고문을 주어로 삼은 부정만 본다 —
     * "확장비는 분양가에 포함되어 있지 않습니다" 같은 내용 주장은 그대로 검사한다.
     */
    private static final Pattern NOT_IN_EXCERPT = Pattern.compile(
            "(발췌|공고문)[^.]{0,40}?(표시|명시|기재|언급|나와\\s*있|적혀\\s*있|확인|찾)[가-힣\\s]{0,6}?(않|없|못)");

    /** 뒤에서 떼는 조사·어미 — 긴 것부터. 한 번 뗀 뒤 한 번 더 본다("제외되며" → "제외되" → "제외") */
    private static final List<String> SUFFIXES = List.of(
            "있습니다", "없습니다", "됩니다", "합니다", "입니다", "습니다", "더라도", "이라도", "으로는", "에서는", "에게는",
            "하여야", "해야", "하며", "되며", "이며", "하고", "되고", "에서", "으로", "에게", "까지", "부터", "이나",
            "하는", "되는", "하면", "되면", "해서", "돼서", "라도", "려면", "이고", "니다",
            "은", "는", "이", "가", "을", "를", "의", "에", "로", "와", "과", "도", "만", "며", "고", "면", "되", "하", "한", "된", "할", "될");

    /** 공고문 어디에나 있어 근거 여부를 가르지 못하는 말 — 겹침 계산에서 뺀다 */
    private static final Set<String> COMMON = Set.of(
            "공고문", "공고", "발췌", "경우", "해당", "신청", "가능", "불가", "불가능", "수", "것", "등", "및", "또는", "그리고",
            "다음", "아래", "위", "관련", "내용", "기준", "대상", "이상", "이하", "이내", "주택", "입주", "입주자", "공급",
            "청약", "필요", "있음", "없음", "있는", "없는", "모든", "각각", "모두", "다만", "단", "따라", "대한", "통해",
            "있으며", "없으며", "있고", "없고", "그", "이", "이를", "이에", "또한", "그러나");

    /** @return 발췌에서 근거를 찾지 못한 문장(원문 그대로, 인용 번호 포함) — 없으면 빈 목록 */
    static List<String> weakSentences(String answer, List<String> excerpts) {
        if (answer == null || answer.isBlank() || excerpts == null || excerpts.isEmpty()) return List.of();
        String corpus = squash(String.join(" ", excerpts));
        List<String> weak = new ArrayList<>();
        for (String raw : splitSentences(answer)) {
            String sentence = CITATION.matcher(raw).replaceAll(" ").strip();
            if (sentence.isEmpty() || GUIDANCE.matcher(sentence).find() || NOT_IN_EXCERPT.matcher(sentence).find()) continue;
            if (!numbersGrounded(sentence, corpus) || !wordsGrounded(sentence, corpus)) weak.add(raw.strip());
        }
        return weak;
    }

    /** 인용 번호는 문장 끝 마침표 뒤에 붙는다("…입니다. [1] 다음 문장") — 그 번호는 앞 문장 것이다 */
    static List<String> splitSentences(String answer) {
        String text = ANY_SPACE.matcher(answer).replaceAll(" ").strip();
        List<String> out = new ArrayList<>();
        for (String piece : SENTENCE.split(text)) {
            Matcher lead = LEADING_CITATIONS.matcher(piece);
            if (lead.find() && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + lead.group(1).strip());
                piece = piece.substring(lead.end());
            }
            if (!piece.isBlank()) out.add(piece.strip());
        }
        return out;
    }

    private static boolean numbersGrounded(String sentence, String corpus) {
        Matcher m = NUMBER_UNIT.matcher(sentence);
        while (m.find()) {
            String unit = m.group(2).equals("퍼센트") ? "%" : m.group(2);
            String n = m.group(1);
            boolean found = corpus.contains(n + unit)
                    || (unit.equals("%") && corpus.contains(n + "퍼센트"));
            if (!found) return false;
        }
        return true;
    }

    private static boolean wordsGrounded(String sentence, String corpus) {
        List<String> words = contentWords(sentence);
        if (words.size() < MIN_WORDS) return true;
        long hit = words.stream().filter(corpus::contains).count();
        return (double) hit / words.size() >= MIN_COVERAGE;
    }

    static List<String> contentWords(String sentence) {
        List<String> out = new ArrayList<>();
        Matcher m = HANGUL_WORD.matcher(sentence);
        while (m.find()) {
            String w = stem(stem(m.group()));
            if (w.length() < 2 || COMMON.contains(w) || out.contains(w)) continue;
            out.add(w);
        }
        return out;
    }

    private static String stem(String word) {
        for (String s : SUFFIXES) {
            if (word.length() - s.length() >= 2 && word.endsWith(s)) return word.substring(0, word.length() - s.length());
        }
        return word;
    }

    private static String squash(String s) {
        return ANY_SPACE.matcher(s).replaceAll("");
    }
}
