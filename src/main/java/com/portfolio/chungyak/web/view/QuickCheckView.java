package com.portfolio.chungyak.web.view;

import java.util.List;

/**
 * 3문항 빠른 진단 결과 화면 모델.
 *
 * 규칙 엔진이 낸 결과를 옮겨 담기만 한다 — 여기서 자격을 다시 따지지 않는다.
 * {@code needsReview} 는 자격 인정이 아니라 "입력한 정보만으로는 탈락이 확정되지 않았다"는 뜻이다.
 */
public record QuickCheckView(
        boolean evaluated,
        List<TypeRow> needsReview,
        List<TypeRow> ruledOut) {

    /** 유형별 결과. ruledOut이면 reason, 추가 확인이 필요하면 remainingInputs를 담는다. */
    public record TypeRow(String typeLabel, String reason, List<String> remainingInputs) {}

    /** 접수중·접수예정 공고가 하나도 없어 진단할 대상이 없는 경우. */
    public static QuickCheckView notEvaluated() {
        return new QuickCheckView(false, List.of(), List.of());
    }

    public static QuickCheckView of(List<TypeRow> needsReview, List<TypeRow> ruledOut) {
        return new QuickCheckView(true, needsReview, ruledOut);
    }

    public boolean hasNeedsReview() {
        return !needsReview.isEmpty();
    }
}
