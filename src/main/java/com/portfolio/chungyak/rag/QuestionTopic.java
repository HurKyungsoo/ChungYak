package com.portfolio.chungyak.rag;

import java.util.regex.Pattern;

/**
 * 공고문 Q&A 질문의 주제 (#40 2단계) — 집계용 분류. 결정론(키워드 사전)이고 질문 원문은 남기지 않는다.
 *
 * 위에서부터 처음 맞는 주제로 정한다. 순서가 곧 우선순위다 — "서류 제출 기간은?"은 일정보다 서류,
 * "청약통장 가입기간 조건"은 일정보다 자격, "잔금은 언제까지"는 일정보다 금액이다.
 * "기타"·NO_MATCH 가 많은 주제는 결정론 답변·동의어 사전을 보강할 근거가 된다(3단계).
 */
public enum QuestionTopic {
    DOCUMENTS("서류", "서류|제출|증명서|등본|초본|원본|사본|구비"),
    REGULATION("규제", "전매|재당첨|거주의무|상한제|실거주"),
    MONEY("금액", "분양가|가격|얼마|금액|계약금|중도금|잔금|보증금|임대료|월세|관리비|대출|이자|취득세|인지세|세금|옵션|확장비|비용|퍼센트|%"),
    SUPPLY("세대수", "세대|가구|물량|몇채|주택형|타입|평형|경쟁률"),
    ELIGIBILITY("자격", "자격|조건|대상|신청할수|신청가능|신청돼|신청되|넣어도|무주택|소득|자산|청약통장|통장|순위|가점|거주기간|해당지역|외국인|부적격|당첨제한|선정|추첨|뽑"),
    SCHEDULE("일정", "언제|며칠|몇일|날짜|일정|발표|접수|마감|시기|몇월|기간|입주예정"),
    LOCATION("위치", "어디|위치|주소|견본주택|모델하우스|교통|학교"),
    OTHER("기타", null);

    private final String label;
    private final Pattern words;

    QuestionTopic(String label, String words) {
        this.label = label;
        this.words = words == null ? null : Pattern.compile(words);
    }

    public String getLabel() {
        return label;
    }

    /** 주제 단어 정규식(OTHER 는 null) — 공고문 청크 주제 실험(eval)에서도 같은 사전을 쓴다 */
    String wordsPattern() {
        return words == null ? null : words.pattern();
    }

    public static QuestionTopic classify(String question) {
        if (question == null) return OTHER;
        String q = question.replaceAll("\s+", "");
        for (QuestionTopic t : values()) {
            if (t.words != null && t.words.matcher(q).find()) return t;
        }
        return OTHER;
    }
}
