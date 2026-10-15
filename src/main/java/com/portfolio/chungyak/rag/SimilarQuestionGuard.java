package com.portfolio.chungyak.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 뜻이 비슷한 질문의 답 재사용(#45) 앞의 <b>결정론 가드</b> — 임베딩 코사인이 높아도 핵심 낱말·숫자·묻는 것이
 * 다르면 재사용하지 않는다.
 *
 * "계약금 비율은?"과 "중도금 비율은?", "1순위 조건"과 "2순위 조건", "견본주택 어디?"와 "견본주택 언제 열어?"는 문장
 * 모양이 거의 같아 코사인이 높게 나온다. 그래서 질문을 {@link Signature}(숫자 · 핵심 낱말 · 묻는 것)로 줄여
 * <b>완전히 같을 때만</b> 코사인을 본다. 사전에 없는 낱말로만 다른 질문은 코사인 기준값이 막는다(두 겹).
 *
 * 질문 원문은 저장하지 않는다 — 캐시에는 이 서명만 둔다(#40 원칙).
 */
final class SimilarQuestionGuard {

    private SimilarQuestionGuard() {}

    /**
     * 질문을 줄인 꼴. 두 질문의 서명이 같아야 같은 답을 쓸 수 있다.
     * @param numbers 질문 안 숫자("1순위"의 1, "84타입"의 84)
     * @param terms   핵심 낱말(정규화한 이름 — "모델하우스"도 "견본주택"), <b>질문에 처음 나온 순서</b>.
     *                "보증금을 올리고 월세를 줄일 수 있나요?"와 그 반대는 낱말이 같고 코사인도 0.95 였다(eval) — 순서로 가른다
     * @param asks    묻는 것(언제·얼마·어디·몇 개·누가·얼마 동안·방법)
     */
    record Signature(Set<String> numbers, List<String> terms, Set<String> asks) {}

    /** 정규화 이름 → 질문(공백 뺀)에서 찾을 정규식. 표기만 다른 말은 한 이름으로 묶는다 */
    private static final Map<String, Pattern> TERMS = new LinkedHashMap<>();
    /** 묻는 것 — "계약금 비율"과 "계약금 언제"를 가른다 */
    private static final Map<String, Pattern> ASKS = new LinkedHashMap<>();

    private static void term(String name, String regex) {
        TERMS.put(name, Pattern.compile(regex));
    }

    private static void ask(String name, String regex) {
        ASKS.put(name, Pattern.compile(regex));
    }

    static {
        // 돈
        term("계약금", "계약금");
        term("중도금", "중도금");
        term("잔금", "잔금");
        term("분양가", "분양가|공급금액|공급가|집값");
        term("보증금", "보증금|전세");
        term("임대료", "임대료|월세|월임대");
        term("관리비", "관리비");
        term("대출", "대출|융자|모기지");
        term("이자", "이자|무이자|후불");
        term("발코니", "발코니|확장");
        term("옵션", "옵션|선택품목");
        term("세금", "취득세|인지세|세금|부가세");
        term("위약금", "위약금|해지|해약");
        term("전환", "전환");
        // 공급 종류
        term("특별공급", "특별공급|특공");
        term("일반공급", "일반공급");
        term("신혼", "신혼|혼인|결혼|예비부부");
        term("다자녀", "다자녀");
        term("생애최초", "생애최초");
        term("노부모", "노부모|부모님?모시");
        term("신생아", "신생아|출산|임신|태아|아기");
        term("청년", "청년");
        term("한부모", "한부모");
        term("우선공급", "우선공급");
        term("잔여", "잔여|무순위|미계약");
        term("예비", "예비입주|예비자|예비당첨");
        term("기관추천", "기관추천");
        // 사람·자격
        term("대학생", "대학생");
        term("고령자", "고령|노인|어르신");
        term("주거급여", "주거급여|수급자");
        term("외국인", "외국인|외국국적");
        term("유주택", "다주택|유주택|1주택|집이?있|집을?가진");
        term("무주택", "무주택");
        term("부부", "부부|배우자|남편|아내");
        term("부모", "부모(?!님?모시)");
        term("세대주", "세대주");
        term("세대원", "세대원|가구원");
        term("소득", "소득|월급|연봉|수입");
        term("자산", "자산|재산");
        term("자동차", "자동차|차량|차가?있");
        term("청약통장", "청약통장|통장|입주자저축|청약저축|주택청약종합저축");
        term("가입기간", "가입기간|가입한지");
        term("가점", "가점");
        term("추첨", "추첨");
        term("나이", "나이|연령|몇살|만\\d+세");
        term("거주", "거주|살아야|사는|실거주");
        term("해당지역", "해당지역");
        term("기타지역", "기타지역");
        term("전매", "전매");
        term("재당첨", "재당첨|당첨제한|청약제한");
        term("부적격", "부적격");
        term("중복", "중복|둘다|각각");
        // 절차·장소
        term("서류", "서류|증명서|등본|초본|원본|사본");
        term("발표", "발표|당첨자명단|당첨확인");
        term("접수", "접수|신청기간|청약일");
        term("계약", "계약(?!금)");
        term("입주", "입주(?!자)|이사");
        term("사전점검", "사전점검|사전방문");
        term("견본주택", "견본주택|모델하우스|주택전시관");
        // 위치·주소는 묻는 것(어디)으로 본다 — "견본주택 어디?"와 "견본주택 주소?"는 같은 질문이다
        term("단지", "단지");
        term("동호", "동호|동·호|호수|층수|층");
        term("임대기간", "임대기간|몇년|최대\\d*년");
        term("분양전환", "분양전환");
        term("취소", "취소|철회");

        // "기간"은 넣지 않는다 — "제한 기간"·"혼인 기간"은 언제가 아니라 얼마 동안이다
        ask("언제", "언제|며칠|몇일|날짜|일정|마감|시기|몇월|몇시");
        ask("얼마", "얼마|비율|퍼센트|%|프로|금액|비용|가격");
        ask("어디", "어디|위치|주소|장소");
        ask("몇개", "몇세대|몇가구|몇명|몇채|몇개|세대수");
        ask("누가", "누가|누구");
        ask("얼마동안", "몇년|몇개월|얼마동안");
        // "어떻게 되나요/돼요"는 "무엇인가"라 뺀다 — "소득 기준이 어떻게 돼요?"와 "소득 기준은?"은 같은 질문이다
        ask("방법", "방법|절차|어떻게(?!되|돼|된)");
    }

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

    static Signature of(String question) {
        String q = question == null ? "" : question.replaceAll("\\s+", "");
        Set<String> numbers = new TreeSet<>();
        Matcher m = NUMBER.matcher(q);
        while (m.find()) numbers.add(m.group().replace(",", ""));
        return new Signature(numbers, ordered(q), matches(ASKS, q));
    }

    /** 서명이 같아야 같은 답을 쓸 수 있다 — 하나라도 다르면 코사인을 보지 않는다 */
    static boolean compatible(Signature a, Signature b) {
        return a.equals(b);
    }

    private static List<String> ordered(String q) {
        Map<String, Integer> first = new LinkedHashMap<>();
        TERMS.forEach((name, p) -> {
            Matcher m = p.matcher(q);
            if (m.find()) first.put(name, m.start());
        });
        return first.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
    }

    private static Set<String> matches(Map<String, Pattern> dict, String q) {
        Set<String> out = new TreeSet<>();
        dict.forEach((name, p) -> {
            if (p.matcher(q).find()) out.add(name);
        });
        return out;
    }
}
