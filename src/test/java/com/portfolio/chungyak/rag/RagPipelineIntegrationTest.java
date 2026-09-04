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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Voyage 로 <b>수집 이후 전체 파이프라인</b>을 한 번에 확인한다:
 * 공고문 원문 → 청크 분할 → 실 임베딩 → 하이브리드 검색(벡터+BM25).
 *
 * VOYAGE_API_KEY 가 있을 때만 돈다. 청크 ~10개 임베딩 ≈ 극소 비용.
 * 실행: VOYAGE_API_KEY=... ./gradlew test --tests '*RagPipelineIntegrationTest*' -i
 */
@EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
class RagPipelineIntegrationTest {

    /** 실제 LH 입주자모집공고문의 형태를 본뜬 샘플 (요건·일정·비용·문의가 섞여 있음). */
    private static final String NOTICE = """
            입주자모집공고 (양주회천 A-26BL 공공분양주택)

            1. 공급개요
            본 주택은 공공주택 특별법에 따라 공급하는 국민주택으로, 전용면적 59㎡ 262세대,
            84㎡ 138세대 총 400세대를 공급합니다. 입주예정일은 2028년 3월입니다.

            2. 신청자격
            입주자모집공고일 현재 경기도에 거주하는 무주택세대구성원으로서 청약통장에 가입하여
            6개월이 지나고 매월 약정납입일에 월납입금을 6회 이상 납입한 자.
            소득 및 자산 보유 기준은 아래 표를 따릅니다.

            3. 특별공급
            다자녀가구, 신혼부부, 생애최초, 노부모부양, 기관추천 특별공급을 시행합니다.
            신혼부부 특별공급은 혼인기간 7년 이내이며 자녀가 있는 경우 우선 배정합니다.
            신생아 특별공급 물량은 이번 공고에 포함되지 않습니다.

            4. 잔여세대 처리
            계약 포기 등으로 발생한 잔여세대는 예비입주자 소진 후 무순위로 별도 공고합니다.
            잔여세대 무순위 접수는 해당 지역 거주요건과 무주택요건만 확인하며 청약통장은 필요하지 않습니다.

            5. 발코니 확장
            전 세대 발코니 확장형으로 공급되며, 확장 비용은 59㎡ 세대당 1,050만원,
            84㎡ 세대당 1,320만원이 계약금과 별도로 부과됩니다.

            6. 계약 및 문의
            당첨자 발표는 2026년 4월 10일 청약홈 및 LH청약센터에서 확인할 수 있습니다.
            기타 문의사항은 LH 콜센터 1600-1004 로 연락하시기 바랍니다.
            """;

    private static VoyageEmbeddingClient voyage;

    @BeforeAll
    static void setUp() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        f.setReadTimeout(Duration.ofSeconds(30));
        RestClient rc = RestClient.builder().requestFactory(f).build();
        voyage = new VoyageEmbeddingClient(rc, new VoyageProperties(
                System.getenv("VOYAGE_API_KEY"),
                System.getenv().getOrDefault("VOYAGE_MODEL", "voyage-4-lite"), null, 0));
    }

    private VectorSearch buildSearch() {
        List<String> texts = new ChunkSplitter(400, 80).split(NOTICE);
        List<float[]> vectors = voyage.embed(texts, InputType.DOCUMENT);

        List<DocumentChunk> chunks = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            chunks.add(new DocumentChunk(1L, i, texts.get(i), vectors.get(i),
                    voyage.model(), "h", Instant.now()));
        }
        System.out.printf("%n[파이프라인] 공고문 %d자 → 청크 %d개 → %d차원 임베딩%n",
                NOTICE.length(), chunks.size(), vectors.get(0).length);

        DocumentChunkRepository repo = mock(DocumentChunkRepository.class);
        when(repo.findAll()).thenReturn(chunks);
        return new VectorSearch(repo, java.util.Optional.of((EmbeddingClient) voyage),
                new RagProperties(new RagProperties.Chunk(400, 80),
                        new RagProperties.Search(3, 0.6), new RagProperties.Qa(4, 0.25, 0.5)));
    }

    @Test
    @DisplayName("질문마다 관련 조항 청크가 1위로 온다 (하이브리드)")
    void retrievesRelevantClause() {
        VectorSearch search = buildSearch();

        record Case(String question, String mustContain) {}
        List<Case> cases = List.of(
                new Case("잔여세대 신청 조건이 어떻게 되나요?", "무순위"),
                new Case("발코니 확장 비용은 얼마인가요?", "확장 비용"),
                new Case("문의 전화번호 알려줘", "1600-1004"),
                new Case("신생아 특별공급도 있나요?", "신생아"),
                new Case("당첨자 발표는 언제인가요?", "당첨자 발표"));

        for (Case c : cases) {
            List<VectorSearch.Hit> hits = search.search(c.question(), 3);
            System.out.printf("%nQ: %s%n", c.question());
            for (VectorSearch.Hit h : hits) {
                System.out.printf("  [%d] 의미 %.2f · 키워드 %.0f%%  %s%n",
                        h.chunkIndex(), h.score(), h.keywordScore() * 100,
                        h.content().replaceAll("\\s+", " ").substring(0, Math.min(70, h.content().length())));
            }
            assertThat(hits.get(0).content()).as("질문: %s", c.question()).contains(c.mustContain());
        }
    }

    @Test
    @DisplayName("공고문에 없는 것을 물으면 상위 발췌의 관련도가 낮다")
    void unrelatedQuestionScoresLow() {
        VectorSearch search = buildSearch();
        List<VectorSearch.Hit> hits = search.search("반려동물을 키워도 되나요?", 3);

        System.out.printf("%nQ(무관): 반려동물 키워도 되나요? → 최고 의미 %.2f · 키워드 %.0f%%%n",
                hits.get(0).score(), hits.get(0).keywordScore() * 100);
        assertThat(hits.get(0).score()).isLessThan(0.5);
        assertThat(hits.get(0).keywordScore()).isLessThan(0.5);
    }
}
