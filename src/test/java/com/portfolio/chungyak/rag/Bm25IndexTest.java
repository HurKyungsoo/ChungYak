package com.portfolio.chungyak.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BM25 키워드 점수 — 형태소 분석기 없이 글자 bi-gram 으로 한국어를 다룬다.
 */
class Bm25IndexTest {

    @Test
    @DisplayName("한글은 bi-gram, 라틴·숫자·전화번호는 통째로 토큰")
    void tokenize() {
        assertThat(Bm25Index.tokenize("발코니 확장")).contains("발코", "코니", "확장");
        assertThat(Bm25Index.tokenize("문의 1600-1004")).contains("1600-1004");
        assertThat(Bm25Index.tokenize("전용 84.97㎡")).contains("84.97");
        assertThat(Bm25Index.tokenize("")).isEmpty();
    }

    @Test
    @DisplayName("질의어가 든 문서가 높은 점수를 받는다")
    void scoresRelevantHigher() {
        Bm25Index index = Bm25Index.build(List.of(
                "입주자모집공고 일반 안내문입니다",
                "발코니 확장 비용은 세대당 별도로 부과됩니다",
                "당첨자 발표일은 공고문을 확인하세요"));

        double[] s = index.scores("발코니 확장 비용");

        assertThat(s[1]).isGreaterThan(s[0]);
        assertThat(s[1]).isGreaterThan(s[2]);
    }

    @Test
    @DisplayName("드물게 나오는 단어(높은 IDF)가 흔한 단어보다 점수 기여가 크다")
    void rareTermWeighsMore() {
        // "공고" 는 모든 문서에, "발코니" 는 하나에만
        Bm25Index index = Bm25Index.build(List.of(
                "공고 안내 공고문 공고",
                "공고 발코니 확장 공고",
                "공고 청약 일정 공고"));

        double rare = index.scores("발코니")[1];
        double common = index.scores("공고")[1];

        assertThat(rare).isGreaterThan(common);
    }

    @Test
    @DisplayName("termCoverage — 질의 토큰 중 문서에 등장한 비율")
    void termCoverage() {
        Bm25Index index = Bm25Index.build(List.of(
                "잔여세대 신청은 무주택자만 가능합니다",
                "관계 없는 문장입니다"));

        assertThat(index.termCoverage("잔여세대 신청", 0)).isEqualTo(1.0);
        assertThat(index.termCoverage("잔여세대 신청", 1)).isZero();
        assertThat(index.termCoverage("", 0)).isZero();
    }

    @Test
    @DisplayName("겹침은 어절 단위 — 의문사·어미는 빼고, 조사가 붙은 어절은 어간으로 본다 (#22 실사용에서 정답 질문이 43% 로 걸러졌다)")
    void coverageIgnoresQuestionWordsAndParticles() {
        Bm25Index index = Bm25Index.build(List.of(
                "신청자격: 무주택세대구성원으로서 소득 및 자산 기준을 충족하는 자",
                "당첨자 발표는 2026.10.26.(월) 청약플러스에서 확인"));

        assertThat(Bm25Index.queryWords("신청 자격이 어떻게 되나요?")).containsExactly("신청", "자격이");
        assertThat(index.termCoverage("신청 자격이 어떻게 되나요?", 0)).isEqualTo(1.0);
        assertThat(index.termCoverage("당첨자 발표일은 언제인가요?", 1)).isEqualTo(1.0);
        // 의문사만 있는 질의는 겹칠 어절이 없다
        assertThat(index.termCoverage("언제 어떻게 되나요?", 0)).isZero();
    }

    @Test
    @DisplayName("빈 코퍼스·매칭 없음은 0")
    void degenerate() {
        assertThat(Bm25Index.build(List.of()).scores("질문")).isEmpty();
        double[] s = Bm25Index.build(List.of("전혀 다른 내용")).scores("발코니");
        assertThat(s).containsExactly(0.0);
    }
}
