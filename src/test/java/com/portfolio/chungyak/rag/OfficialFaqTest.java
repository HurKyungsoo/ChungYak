package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.rag.embedding.Reranker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OfficialFaqTest {

    private static final OfficialFaq FAQ = OfficialFaq.load();

    @Test
    @DisplayName("국토부 2024 FAQ 480문항 — 질문·답이 모두 있고 번호가 1..480")
    void loads() {
        assertThat(FAQ.items()).hasSize(480);
        assertThat(FAQ.items()).allSatisfy(i -> {
            assertThat(i.question()).isNotBlank();
            assertThat(i.answer()).hasSizeGreaterThan(20);
        });
        assertThat(FAQ.item(42).question()).contains("보이스피싱");
        assertThat(FAQ.basisDate()).isEqualTo("2024-05-29");
    }

    @Test
    @DisplayName("문항 사이 조문 상자·절 개요가 답에 붙지 않는다 (처음 파싱에선 Q120 답에 제2조 정의가 붙었다)")
    void answerBoundaries() {
        assertThat(FAQ.item(120).answer()).doesNotContain("제2조(정의)").endsWith("합니다.");
    }

    @Test
    @DisplayName("기준일 뒤 개정 경고 — 소형·저가주택(2024-12-18), 특별공급 횟수·신혼부부(2025-03-31), 거주지역 일반 문항은 없음")
    void amendments() {
        assertThat(FAQ.amendmentsFor(FAQ.item(109))).extracting(OfficialFaq.Amendment::effective).contains("2024-12-18");
        assertThat(FAQ.amendmentsFor(FAQ.item(146))).extracting(OfficialFaq.Amendment::effective).contains("2025-03-31");
        assertThat(FAQ.amendmentsFor(FAQ.item(1))).isEmpty();
        assertThat(FAQ.amendments()).allSatisfy(a -> assertThat(a.reason()).doesNotStartWith("【"));
    }

    @Test
    @DisplayName("화면용 답 — PDF 줄바꿈은 잇고, 들여쓴 줄·목록 기호 줄은 새 문단, 낱말은 그대로")
    void displayAnswer() {
        OfficialFaq.Item it = new OfficialFaq.Item(1, null, null, 1, "q",
                "해당 지역이란 지방\n자치단체를 말합니다.\n     이후 남은 물량은\n공급합니다.\n* (예) 2020.1.1");
        assertThat(it.displayAnswer()).isEqualTo("해당 지역이란 지방 자치단체를 말합니다.\n이후 남은 물량은 공급합니다.\n* (예) 2020.1.1");
    }

    private static Reranker fixedScores(double... scores) {
        return new Reranker() {
            @Override public List<Integer> order(String q, List<String> d) { throw new UnsupportedOperationException(); }
            @Override public double[] scores(String q, List<String> d) {
                double[] out = new double[d.size()];
                for (int i = 0; i < out.length && i < scores.length; i++) out[i] = scores[i];
                return out;
            }
            @Override public int candidates() { return 20; }
            @Override public String model() { return "fake"; }
        };
    }

    @Test
    @DisplayName("검색 — rerank 1위가 기준(0.60) 미만이면 없음, 이상이면 1위 + 기준 넘는 다음 문항, 재정렬기 없으면 꺼짐")
    void searchGate() {
        assertThat(new OfficialFaqSearch(FAQ, Optional.empty(), Optional.empty(), 0.60).search("보이스피싱 통장 해지")).isEmpty();
        assertThat(new OfficialFaqSearch(FAQ, Optional.empty(), Optional.of(fixedScores(0.52, 0.4)), 0.60)
                .search("견본주택 어디 있어요?")).isEmpty();

        var m = new OfficialFaqSearch(FAQ, Optional.empty(), Optional.of(fixedScores(0.93, 0.70, 0.30)), 0.60)
                .search("보이스피싱 당해서 청약통장 해지됐는데 살릴 수 있어요?");
        assertThat(m).isPresent();
        assertThat(m.get().score()).isEqualTo(0.93);
        assertThat(m.get().related()).hasSize(1);
        assertThat(m.get().basisDate()).isEqualTo("2024-05-29");
    }

    @Test
    @DisplayName("검색 — 재정렬 실패하면 없음(Q&A 를 막지 않는다), 후보 문서는 제목 + 답 앞부분")
    void searchFailureAndDocs() {
        AtomicReference<List<String>> seen = new AtomicReference<>();
        Reranker failing = new Reranker() {
            @Override public List<Integer> order(String q, List<String> d) { throw new UnsupportedOperationException(); }
            @Override public double[] scores(String q, List<String> d) { seen.set(d); throw new IllegalStateException("429"); }
            @Override public int candidates() { return 20; }
            @Override public String model() { return "fake"; }
        };
        assertThat(new OfficialFaqSearch(FAQ, Optional.empty(), Optional.of(failing), 0.60).search("분양권 있으면 무주택이에요?")).isEmpty();
        assertThat(seen.get()).hasSize(20).allSatisfy(d -> assertThat(d.length()).isLessThanOrEqualTo(800));
    }
}
