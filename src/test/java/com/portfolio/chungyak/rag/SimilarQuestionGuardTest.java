package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.SimilarQuestionEvalTest.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
    @DisplayName("표기만 다른 질문은 통과 — 모델하우스=견본주택, 퍼센트=비율, 어떻게 돼요=은요")
    void sameMeaningPasses() {
        assertThat(compatible("계약금은 몇 퍼센트예요?", "계약금 비율이 얼마예요?")).isTrue();
        assertThat(compatible("견본주택은 어디 있어요?", "모델하우스 어디 있어요?")).isTrue();
        assertThat(compatible("견본주택은 어디 있어요?", "견본주택 주소가 어디예요?")).isTrue();
        assertThat(compatible("소득 기준이 어떻게 되나요?", "소득 기준은요?")).isTrue();
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
    @DisplayName("eval 쌍 — 손으로 쓴 다른 뜻 쌍은 전부 막고, 같은 뜻 쌍은 28/30 이상 통과")
    void evalPairs() throws IOException {
        List<Pair> pairs = SimilarQuestionEvalTest.readPairs();
        assertThat(pairs.stream().filter(p -> !p.same() && compatible(p.a(), p.b())).map(p -> p.a() + " / " + p.b()))
                .isEmpty();
        assertThat(pairs.stream().filter(p -> p.same() && compatible(p.a(), p.b())).count()).isGreaterThanOrEqualTo(28);
    }

    @Test
    @DisplayName("같은 공고의 eval 질문끼리(337쌍, 따로 쓴 다른 질문)는 거의 다 막는다 — 통과는 코사인이 막는다")
    void sameNoticeQuestionsMostlyBlocked() throws IOException {
        List<Pair> pairs = SimilarQuestionEvalTest.sameNoticePairs(SimilarQuestionEvalTest.readPairs());
        assertThat(pairs).hasSizeGreaterThan(300);
        assertThat(pairs.stream().filter(p -> compatible(p.a(), p.b())).count()).isLessThanOrEqualTo(1);
    }
}
