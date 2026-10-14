package com.portfolio.chungyak.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 청약 용어 동의어 사전 — 일상어 질문을 공고문 용어로 넓힌다 (#28).
 *
 * BM25 는 글자가 겹쳐야 찾는다. "돈 얼마 내야 들어가?"는 공고문의 "임대보증금·월임대료·계약금"과 한 글자도
 * 겹치지 않아 놓친다(#22 실사용: 의역 3/3 놓침). 공고문 분야가 좁아 자주 나오는 일상어는 사전으로 충분히 잡힌다.
 *
 * 결정론적이다 — 질의를 넓힐 뿐 판정·답변에는 관여하지 않는다. 키는 질의 어절의 <b>앞부분</b>과 맞춘다
 * ("이사"는 "이사를"·"이사갈"도 잡는다). 사전에 없는 표현은 그대로 BM25 로 간다.
 */
final class QuerySynonyms {

    private QuerySynonyms() {}

    private static final List<String> MONEY = List.of("보증금", "임대료", "계약금", "분양가", "공급금액", "중도금", "잔금");

    /** 어절 앞부분 → 공고문 용어. 순서대로 처음 맞는 하나만 쓴다(긴 키를 앞에). */
    private static final Map<String, List<String>> BY_PREFIX = new LinkedHashMap<>();

    static {
        // 돈
        BY_PREFIX.put("월세", List.of("월임대료", "임대료"));
        BY_PREFIX.put("전세", List.of("임대보증금", "보증금"));
        BY_PREFIX.put("집값", List.of("분양가", "공급금액"));
        BY_PREFIX.put("가격", MONEY);
        BY_PREFIX.put("비용", MONEY);
        BY_PREFIX.put("내야", MONEY);
        BY_PREFIX.put("내는", MONEY);
        BY_PREFIX.put("돈", MONEY);
        // 입주
        BY_PREFIX.put("이사", List.of("입주", "입주예정"));
        BY_PREFIX.put("들어가", List.of("입주", "입주예정"));
        BY_PREFIX.put("들어갈", List.of("입주", "입주예정"));
        BY_PREFIX.put("살수", List.of("입주", "거주"));
        // 소득·자산
        BY_PREFIX.put("월급", List.of("소득", "월평균소득"));
        BY_PREFIX.put("연봉", List.of("소득", "월평균소득"));
        BY_PREFIX.put("수입", List.of("소득"));
        BY_PREFIX.put("벌이", List.of("소득"));
        BY_PREFIX.put("재산", List.of("자산", "총자산"));
        BY_PREFIX.put("차량", List.of("자동차", "자동차가액"));
        BY_PREFIX.put("차는", List.of("자동차", "자동차가액"));
        // 사람
        BY_PREFIX.put("결혼", List.of("혼인", "신혼부부"));
        BY_PREFIX.put("애가", List.of("자녀"));
        BY_PREFIX.put("아이", List.of("자녀", "태아"));
        BY_PREFIX.put("나이", List.of("연령", "만19세"));
        BY_PREFIX.put("부모", List.of("직계존속", "노부모"));
        // 절차·일정
        BY_PREFIX.put("준비물", List.of("제출서류", "구비서류"));
        BY_PREFIX.put("서류", List.of("제출서류", "구비서류"));
        BY_PREFIX.put("합격", List.of("당첨자", "발표"));
        BY_PREFIX.put("붙었", List.of("당첨자", "발표"));
        BY_PREFIX.put("마감", List.of("접수기간", "신청기간"));
        BY_PREFIX.put("집있", List.of("주택소유", "무주택"));
        BY_PREFIX.put("집이", List.of("주택소유", "무주택"));
        BY_PREFIX.put("집을", List.of("주택소유", "무주택"));
        BY_PREFIX.put("청약통장", List.of("입주자저축", "주택청약종합저축"));
        BY_PREFIX.put("통장", List.of("입주자저축", "주택청약종합저축"));
        // #39 eval 에서 놓친 일상어
        BY_PREFIX.put("모델하우스", List.of("견본주택"));
        BY_PREFIX.put("구경", List.of("견본주택", "주택개방"));
        BY_PREFIX.put("사본", List.of("원본", "사본"));
        BY_PREFIX.put("뽑", List.of("선정", "추첨"));
        BY_PREFIX.put("넣어", List.of("청약", "신청"));
        BY_PREFIX.put("넣으", List.of("청약", "신청"));
        // (#42, 2026-10-14) 운영 검색이 놓친 질문용 동의어(선정→선정방법, 고르다→지정·개방, 다주택→1주택 …)를 넣어 봤지만
        // 운영 경로(rerank-20)는 62 → 62 그대로였고 "계약할→계약체결"은 표 값 질문 하나를 놓치게 했다 — 넣지 않는다.
    }

    /**
     * 동의어로 <b>바꾸는</b> 어절 — 공고문에 쓰이지 않는 말이라 원래 글자는 검색어에서 뺀다 (#39).
     * "모델하우스"를 남겨 두면 "하우스" 조각이 엉뚱한 청크(커뮤니티 시설 안내)에 걸려 "견본주택" 청크를 이겼다.
     */
    private static final List<String> REPLACED = List.of("모델하우스");

    static boolean replaces(String word) {
        return REPLACED.stream().anyMatch(word::startsWith);
    }

    /**
     * 일상어 → 공고문이 쓰는 <b>바로 그 말</b>(1:1) (#39). 검색 확장({@link #BY_PREFIX})과 달리 뜻이 하나로 정해지는 것만 —
     * LLM 에 용어 힌트로 주고("모델하우스" 질문에 "견본주택" 발췌를 보고도 "확인할 수 없다"고 답했다),
     * 답을 못 찾았을 때 "공고문 용어로 다시 묻기"를 만든다. "돈→보증금·분양가…" 처럼 갈래가 여럿인 말은 넣지 않는다.
     */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("모델하우스", "견본주택");
        ALIASES.put("월세", "월임대료");
        ALIASES.put("전세금", "임대보증금");
        ALIASES.put("집값", "분양가");
        ALIASES.put("청약통장", "입주자저축");
        ALIASES.put("이사", "입주");
        ALIASES.put("합격", "당첨");
        ALIASES.put("결혼", "혼인");
        ALIASES.put("준비물", "제출서류");
        ALIASES.put("연봉", "소득");
        ALIASES.put("월급", "소득");
        ALIASES.put("재산", "자산");
    }

    /** 질문에 나온 일상어 → 공고문 용어 (순서 유지, 없으면 빈 맵) */
    static Map<String, String> termHints(String question) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String w : Bm25Index.queryWords(question)) {
            ALIASES.forEach((k, v) -> { if (w.startsWith(k)) out.put(k, v); });
        }
        return out;
    }

    /** LLM 질문 끝에 붙이는 용어 힌트 한 줄 — 없으면 빈 문자열 */
    static String hintLine(String question) {
        Map<String, String> hints = termHints(question);
        if (hints.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n(용어: ");
        hints.forEach((k, v) -> sb.append("질문의 '").append(k).append("' = 공고문의 '").append(v).append("', "));
        sb.setLength(sb.length() - 2);
        return sb.append(")").toString();
    }

    /** 일상어를 공고문 용어로 바꾼 질문 — 바꿀 말이 없으면 empty */
    static java.util.Optional<String> rephrase(String question) {
        Map<String, String> hints = termHints(question);
        if (hints.isEmpty()) return java.util.Optional.empty();
        String out = question.strip();
        for (var e : hints.entrySet()) out = out.replace(e.getKey(), e.getValue());
        return java.util.Optional.of(out);
    }

    /** 질의 어절 하나의 동의어 — 없으면 빈 목록 */
    static List<String> of(String word) {
        for (var e : BY_PREFIX.entrySet()) {
            if (word.startsWith(e.getKey())) return e.getValue();
        }
        return List.of();
    }
}
