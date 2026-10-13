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
    }

    /** 질의 어절 하나의 동의어 — 없으면 빈 목록 */
    static List<String> of(String word) {
        for (var e : BY_PREFIX.entrySet()) {
            if (word.startsWith(e.getKey())) return e.getValue();
        }
        return List.of();
    }
}
