package com.portfolio.chungyak.rag;

import com.portfolio.chungyak.domain.DocumentChunk;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient;
import com.portfolio.chungyak.rag.embedding.EmbeddingClient.InputType;
import com.portfolio.chungyak.rag.embedding.VoyageEmbeddingClient;
import com.portfolio.chungyak.rag.embedding.VoyageProperties;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Voyage 로 <b>수집 이후 전체 파이프라인</b>을 한 번에 확인한다:
 * 공고문 원문 → 청크 분할 → 실 임베딩 → 하이브리드 검색(벡터+BM25).
 *
 * VOYAGE_API_KEY 가 있을 때만 돈다. 결제수단 미등록 계정은 3 RPM 제한이라
 * {@link VoyageEmbeddingClient} 의 백오프로 재시도하며 몇 분 걸릴 수 있다.
 * 실행: VOYAGE_API_KEY=... ./gradlew test --tests '*RagPipelineIntegrationTest*' -i
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagPipelineIntegrationTest {

    /** 실제 LH 입주자모집공고문의 형태를 본뜬 샘플 — 조항마다 다른 주제. */
    private static final String NOTICE = """
            입주자모집공고 — 양주회천 A-26BL 공공분양주택

            제1조 (공급개요)
            본 주택은 공공주택 특별법에 따라 공급하는 국민주택으로 전용면적 59제곱미터 262세대,
            84제곱미터 138세대, 총 400세대를 공급한다. 입주예정일은 2028년 3월이다.

            제2조 (신청자격)
            입주자모집공고일 현재 경기도에 거주하는 무주택세대구성원으로서, 청약통장에 가입하여
            6개월이 지나고 매월 약정납입일에 월납입금을 6회 이상 납입한 사람이어야 한다.
            세대의 월평균소득과 총자산은 별표의 기준을 초과하지 않아야 한다.

            제3조 (특별공급)
            다자녀가구, 신혼부부, 생애최초, 노부모부양, 기관추천 특별공급을 시행한다.
            신혼부부 특별공급은 혼인기간 7년 이내인 자에게 공급하며 미성년 자녀가 있으면 우선 배정한다.
            이번 공고에는 신생아 특별공급 물량이 배정되지 않는다.

            제4조 (잔여세대 처리)
            계약 포기 등으로 발생한 잔여세대는 예비입주자를 모두 소진한 후 무순위로 별도 공고한다.
            잔여세대 무순위 접수 시에는 해당 지역 거주요건과 무주택요건만 확인하며, 청약통장 보유는 요구하지 않는다.

            제5조 (발코니 확장 비용)
            전 세대 발코니 확장형으로 공급하며, 확장 비용은 전용 59제곱미터 세대당 1,050만원,
            84제곱미터 세대당 1,320만원으로 계약금과 별도로 납부하여야 한다.

            제6조 (당첨자 발표 및 문의)
            당첨자 발표는 2026년 4월 10일 청약홈과 LH청약센터 홈페이지에 게시한다.
            기타 문의사항은 LH 콜센터 1600-1004 로 문의한다.
            """;

    private VectorSearch search;

    @BeforeAll
    void setUp() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(30));
        RestClient rc = RestClient.builder().requestFactory(f).build();
        VoyageEmbeddingClient voyage = new VoyageEmbeddingClient(rc, new VoyageProperties(
                System.getenv("VOYAGE_API_KEY"),
                System.getenv().getOrDefault("VOYAGE_MODEL", "voyage-4-lite"), null, 0));

        List<String> texts = new ChunkSplitter(320, 60).split(NOTICE);
        List<float[]> vectors = voyage.embed(texts, InputType.DOCUMENT);   // 문서 임베딩 1회

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            chunks.add(new DocumentChunk(1L, i, texts.get(i), vectors.get(i),
                    voyage.model(), "h", Instant.now()));
        }
        System.out.printf("%n[파이프라인] 공고문 %d자 → 청크 %d개 → %d차원 임베딩 (model=%s)%n",
                NOTICE.length(), chunks.size(), vectors.get(0).length, voyage.model());

        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findAll()).thenReturn(chunks);
        search = new VectorSearch(repo, Optional.of((EmbeddingClient) voyage),
                new RagProperties(new RagProperties.Chunk(320, 60),
                        new RagProperties.Search(3, 0.6), new RagProperties.Qa(4, 0.25, 0.5)));
    }

    @Test
    @DisplayName("질문마다 관련 조항 청크가 1위로 온다 (하이브리드)")
    void retrievesRelevantClause() {
        record Case(String question, String mustContain) {}
        List<Case> cases = List.of(
                new Case("잔여세대는 어떤 조건으로 신청하나요?", "무순위"),
                new Case("발코니 확장 비용이 얼마인가요?", "확장 비용"),
                new Case("문의 전화번호 알려줘", "1600-1004"));

        for (Case c : cases) {
            List<VectorSearch.Hit> hits = search.search(c.question(), 3);
            System.out.printf("%nQ: %s%n", c.question());
            for (VectorSearch.Hit h : hits) {
                String snip = h.content().replaceAll("\\s+", " ");
                System.out.printf("  [%d] 의미 %.2f · 키워드 %2.0f%%  %s%s%n",
                        h.chunkIndex(), h.score(), h.keywordScore() * 100,
                        snip.substring(0, Math.min(90, snip.length())),
                        snip.length() > 90 ? " …" : "");
            }
            assertThat(hits.get(0).content()).as("질문: %s → 1위 청크에 '%s' 포함",
                    c.question(), c.mustContain()).contains(c.mustContain());
        }
    }

    @Test
    @DisplayName("공고문에 없는 것을 물으면 상위 발췌의 관련도가 낮다")
    void unrelatedQuestionScoresLow() {
        List<VectorSearch.Hit> hits = search.search("반려동물을 키워도 되나요?", 3);
        VectorSearch.Hit top = hits.get(0);
        System.out.printf("%nQ(무관): 반려동물 키워도 되나요? → 최고 의미 %.2f · 키워드 %.0f%%%n",
                top.score(), top.keywordScore() * 100);
        assertThat(top.score()).isLessThan(0.5);
        assertThat(top.keywordScore()).isLessThan(0.5);
    }
}
