package com.portfolio.chungyak.web.view;

import java.util.List;

/**
 * 3문항 빠른 진단 결과 화면 모델.
 *
 * 규칙 엔진이 낸 결과를 옮겨 담기만 한다 — 여기서 자격을 다시 따지지 않는다.
 * {@code possible} 은 <b>자격 인정이 아니라 "아직 탈락 사유가 없다"</b>는 뜻이고,
 * 화면도 반드시 그렇게 표시해야 한다(확정 판정과 섞이면 이 서비스의 존재 이유가 사라진다).
 */
public record QuickCheckView(
        boolean evaluated,
        List<TypeRow> possible,
        List<TypeRow> ruledOut,
        List<String> remainingInputs) {

    /** 유형 하나. ruledOut 이면 {@code reason} 에 엔진이 낸 탈락 사유가 들어간다. */
    public record TypeRow(String typeLabel, String reason) {}

    /** 접수중·접수예정 공고가 하나도 없어 진단할 대상이 없는 경우. */
    public static QuickCheckView notEvaluated() {
        return new QuickCheckView(false, List.of(), List.of(), List.of());
    }

    public static QuickCheckView of(List<TypeRow> possible, List<TypeRow> ruledOut, List<String> remainingInputs) {
        return new QuickCheckView(true, possible, ruledOut, remainingInputs);
    }

    public boolean hasPossible() {
        return !possible.isEmpty();
    }
}
