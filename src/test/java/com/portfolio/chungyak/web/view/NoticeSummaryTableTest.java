package com.portfolio.chungyak.web.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 청약홈 공고문 첫 페이지 "단지 주요정보" 표 (#38) — 머리는 2026-10-13 실제 PDF 추출 결과를 옮긴 것. */
class NoticeSummaryTableTest {

    /** 2026000469(부산 민영) 첫 페이지 */
    private static final String BUSAN = """
            주택유형 해당지역 기타지역 규제지역여부
            부산광역시 1년 이상 계속 거주자 부산광역시 1년 미만 계속 거주자
            민영 비규제지역
            재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형
            없음 6개월 없음 미적용 민간택지
            구분 입주자모집공고일 특별공급 접수일 일반공급 1순위 접수일
            """;

    /** 2026000402(전북개발공사 10년 공공임대) — 요약표 없이 자유 서술 */
    private static final String PUBLIC_RENTAL = """
            ■ 이 주택의 최초 입주자모집공고일은 2026.10.08(목)이며, 이는 청약자격(청약신청, 자격조건의 기간, 나이,
            세대구성원, 지역우선, 주택소유, 재당첨제한 등)의 판단기준일이 됩니다.
            """;

    @Test
    @DisplayName("머리 줄 다음 줄의 값 다섯 칸을 그대로 옮긴다")
    void parsesStandardTable() {
        var table = NoticeSummaryTable.parse(BUSAN).orElseThrow();

        assertThat(table.items()).extracting(NoticeSummaryTable.Item::label)
                .containsExactly("재당첨제한", "전매제한", "거주의무기간", "분양가상한제", "택지유형");
        assertThat(table.items()).extracting(NoticeSummaryTable.Item::value)
                .containsExactly("없음", "6개월", "없음", "미적용", "민간택지");
    }

    @Test
    @DisplayName("표가 없는 공고(공공 자유 서술)는 없음 — 본문에 '재당첨제한' 단어가 있어도 표로 보지 않는다")
    void noTableInFreeText() {
        assertThat(NoticeSummaryTable.parse(PUBLIC_RENTAL)).isEmpty();
        assertThat(NoticeSummaryTable.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("값 칸이 다섯 개로 나뉘지 않으면(칸 안 공백 등) 추측하지 않고 버린다")
    void rejectsMisalignedValues() {
        String misaligned = "재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형\n10년 1년 6개월 없음 적용 공공택지\n";
        assertThat(NoticeSummaryTable.parse(misaligned)).isEmpty();
    }

    @Test
    @DisplayName("첫 페이지(앞 5,000자)에서만 찾는다 — 본문 깊은 곳의 같은 나열은 표가 아니다")
    void onlyLooksAtHead() {
        String deep = "가".repeat(6_000) + "\n재당첨제한 전매제한 거주의무기간 분양가상한제 택지유형\n없음 없음 없음 미적용 민간택지\n";
        assertThat(NoticeSummaryTable.parse(deep)).isEmpty();
    }
}
