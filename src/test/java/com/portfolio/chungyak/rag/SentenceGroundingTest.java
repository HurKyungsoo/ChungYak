package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 답 문장 근거 검사 (#41) — 발췌는 실제 공고문 문장(2997·3018). */
class SentenceGroundingTest {

    private static final List<String> HOPE_TOWN = List.of(
            "[1] 혼인 중인 신혼부부로서 혼인기간이 7년 이내(2019.09.30.~2026.09.30.에 속한 자) 또는 6세 이하의 자녀"
                    + "(만 7세 미만으로, 태아 포함)를 둔 무주택세대구성원 ② 입주자저축에 가입하여 6개월이 경과되고, 매월 약정납입일에"
                    + " 월납입금을 6회 이상 납입한 분");

    private static final List<String> YOUTH = List.of(
            "[1] 임대기간: • 2년, 재계약 4회 가능(자격 충족 시 최장 10년 거주) - 입주 후 혼인한 경우 재계약 5회 추가연장 가능"
                    + "(자격 충족 시 최장 20년 거주)");

    @Test
    @DisplayName("발췌를 말만 바꿔 정리한 문장은 통과")
    void paraphraseIsGrounded() {
        assertThat(SentenceGrounding.weakSentences(
                "혼인기간이 7년 이내이거나 6세 이하 자녀를 둔 무주택세대구성원이 신청할 수 있습니다. [1] "
                        + "입주자저축 가입 후 6개월이 지나고 6회 이상 납입해야 합니다. [1]", HOPE_TOWN)).isEmpty();
    }

    @Test
    @DisplayName("숫자를 바꾼 문장은 잡는다 — 혼인 7년 → 10년")
    void changedNumberIsCaught() {
        assertThat(SentenceGrounding.weakSentences(
                "혼인기간이 7년 이내면 신청할 수 있습니다. [1] 혼인기간이 10년 이내인 신혼부부도 신청할 수 있습니다. [1]", HOPE_TOWN))
                .containsExactly("혼인기간이 10년 이내인 신혼부부도 신청할 수 있습니다. [1]");
    }

    @Test
    @DisplayName("발췌에 없는 내용을 덧붙인 문장은 잡는다")
    void inventedContentIsCaught() {
        assertThat(SentenceGrounding.weakSentences(
                "자격을 충족하면 최장 10년 거주할 수 있습니다. [1] 기숙사 우선 배정과 관리비 감면 혜택도 함께 받습니다. [1]", YOUTH))
                .containsExactly("기숙사 우선 배정과 관리비 감면 혜택도 함께 받습니다. [1]");
    }

    @Test
    @DisplayName("안내 문장·짧은 문장은 보지 않는다")
    void guidanceAndShortSentencesAreSkipped() {
        assertThat(SentenceGrounding.weakSentences(
                "공고문 발췌에서는 확인되지 않습니다. 원문을 확인하세요.", YOUTH)).isEmpty();
        assertThat(SentenceGrounding.weakSentences("외국인은 신청할 수 없습니다. [1]", YOUTH)).isEmpty();
        // 운영 2901 — 못 찾았다는 답에 경고가 같은 말을 반복했다
        assertThat(SentenceGrounding.weakSentences(
                "공고문 발췌에서는 월 임대료 금액이 명확히 표시되지 않습니다. 원문을 확인하세요.", YOUTH)).isEmpty();
    }

    @Test
    @DisplayName("발췌를 주어로 삼지 않은 부정은 내용 주장이라 그대로 검사한다")
    void contentNegationIsStillChecked() {
        assertThat(SentenceGrounding.weakSentences(
                "발코니 확장 비용은 분양가에 포함되어 있지 않으며 별도 계약으로 납부합니다.", YOUTH)).isNotEmpty();
    }

    @Test
    @DisplayName("인용 번호는 앞 문장에 붙여 나눈다")
    void splitsWithCitations() {
        assertThat(SentenceGrounding.splitSentences("가능합니다. [1][2] 다음 문장입니다. [3]"))
                .containsExactly("가능합니다. [1][2]", "다음 문장입니다. [3]");
    }

    @Test
    @DisplayName("퍼센트는 '%'·'퍼센트' 어느 쪽으로 적혀도 같다")
    void percentEitherWay() {
        List<String> ex = List.of("[1] 월평균소득 합계가 가구당 월평균소득의 100퍼센트 이하");
        assertThat(SentenceGrounding.weakSentences("월평균소득 합계가 가구당 월평균소득의 100% 이하여야 합니다. [1]", ex)).isEmpty();
    }
}
