package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.SimilarQuestionEvalTest.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비슷한 질문 재사용 가드(#45) — 오프라인. 코사인 기준값은 {@link SimilarQuestionEvalTest}(Voyage 키 있을 때)가 정한다.
 */
class SimilarQuestionGuardTest {

    private static boolean compatible(String a, String b) {
        return SimilarQuestionGuard.compatible(SimilarQuestionGuard.of(a), SimilarQuestionGuard.of(b));
    }

    @Test
    @DisplayName("표기·어미만 다른 질문은 통과 — 모델하우스=견본주택, 어떻게 돼요=은요, 할 수 있어요=되나요")
    void sameMeaningPasses() {
        assertThat(compatible("견본주택은 어디 있어요?", "모델하우스 어디 있어요?")).isTrue();
        assertThat(compatible("견본주택은 어디 있어요?", "견본주택 주소가 어디예요?")).isTrue();
        assertThat(compatible("소득 기준이 어떻게 되나요?", "소득 기준은요?")).isTrue();
        assertThat(compatible("대학생도 신청할 수 있어요?", "대학생도 되나요?")).isTrue();
        assertThat(compatible("퇴사했는데 소득 어떻게 계산해요?", "퇴사하면 소득은 어떻게 계산하나요?")).isTrue();
    }

    @Test
    @DisplayName("핵심 낱말·숫자·묻는 것·낱말 순서가 다르면 막는다")
    void differentMeaningBlocked() {
        assertThat(compatible("계약금은 몇 퍼센트예요?", "중도금은 몇 퍼센트예요?")).isFalse();
        assertThat(compatible("1순위 조건이 뭐예요?", "2순위 조건이 뭐예요?")).isFalse();
        assertThat(compatible("계약금은 언제 내요?", "계약금은 얼마 내요?")).isFalse();
        assertThat(compatible("보증금을 올리고 월세를 줄일 수 있나요?", "월세를 올리고 보증금을 줄일 수 있나요?")).isFalse();
        assertThat(compatible("임대기간 끝나면 어떻게 되나요?", "임대기간은 몇 년이에요?")).isFalse();
    }

    @Test
    @DisplayName("사전 밖 낱말이 다르면 막는다 — hold-out 오탐: 몇 시까지/부터(코사인 0.94), 이혼/재혼(0.91)")
    void residualWordsBlocked() {
        assertThat(compatible("청약 신청 몇 시까지예요?", "청약 신청 몇 시부터예요?")).isFalse();
        assertThat(compatible("이혼했는데 신혼부부로 신청돼요?", "재혼했는데 신혼부부로 신청돼요?")).isFalse();
        assertThat(compatible("주말에도 견본주택 열어요?", "공휴일에도 견본주택 열어요?")).isFalse();
    }

    @Test
    @DisplayName("무엇을 묻는지 알 수 없는 질문은 재사용하지 않는다")
    void emptySignatureNeverCompatible() {
        assertThat(compatible("뭐예요?", "뭐예요?")).isFalse();
    }

    @Test
    @DisplayName("eval 쌍 전부 — 다른 뜻 쌍(손·같은 공고·hold-out 1·2)은 하나도 통과하지 않는다")
    void evalPairsDifferentMeaningAllBlocked() throws IOException {
        List<Pair> hand = SimilarQuestionEvalTest.readPairs();
        List<Pair> pairs = new ArrayList<>(hand);
        pairs.addAll(SimilarQuestionEvalTest.sameNoticePairs(hand));
        pairs.addAll(SimilarQuestionEvalTest.readHoldout());
        pairs.addAll(SimilarQuestionEvalTest.readHoldout2());
        assertThat(pairs.stream().filter(p -> !p.same()).count()).isGreaterThan(400);

        assertThat(pairs.stream().filter(p -> !p.same() && compatible(p.a(), p.b()))
                .map(p -> "[" + p.origin() + "] " + p.a() + " / " + p.b())).isEmpty();
        // 같은 뜻 54쌍 중 가드 통과 15 (2026-10-15) — 사전 밖 낱말 검사로 많이 놓치지만, 놓치면 LLM 을 한 번 더 부를 뿐이다
        assertThat(pairs.stream().filter(p -> p.same() && compatible(p.a(), p.b())).count()).isGreaterThanOrEqualTo(15);
    }
}
