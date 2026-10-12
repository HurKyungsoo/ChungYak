package com.portfolio.chungyak.web.view;

import java.util.List;

/**
 * "이것만 바뀌면 가능" — 지금은 신청할 수 없지만 같은 보완으로 신청 가능해지는 공고 묶음.
 *
 * 묶는 기준은 엔진이 남긴 {@code improvementHints} 문장 집합 그대로다(유형 하나의 힌트 전부).
 * 힌트에는 공고마다 다른 요건 수치가 들어가므로(비규제 6개월·규제 24개월 등) 같은 문장끼리만 묶여
 * 숫자가 섞이지 않는다. 새 수치를 계산하지 않는다 — 판정·격차 계산은 규칙이 이미 했다.
 *
 * @param hints         이 보완을 <b>모두</b> 하면 된다 (한 유형의 힌트 전부)
 * @param announcements 그러면 신청 가능해지는 공고와 그 유형
 */
public record FixableGroup(List<String> hints, List<Item> announcements) {

    public record Item(Long id, String houseName, String typeLabel) {}

    /** 공고 수 — announcements 는 공고당 한 줄(유형은 typeLabel 에 합쳐 있다) */
    public int count() {
        return announcements.size();
    }
}
