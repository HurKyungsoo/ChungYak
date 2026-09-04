package com.portfolio.chungyak.web;

import com.portfolio.chungyak.rag.AnnouncementIndexer;
import com.portfolio.chungyak.rag.RagIndexReport;
import com.portfolio.chungyak.rag.VectorSearch;
import com.portfolio.chungyak.repository.AnnouncementDocumentRepository;
import com.portfolio.chungyak.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 관리자 RAG 인덱싱 API. {@code /api/admin/**} = ROLE_ADMIN (SecurityConfig).
 *
 *   curl -u admin:$ADMIN_PASSWORD -X POST http://localhost:8080/api/admin/rag/reindex
 *   curl -u admin:$ADMIN_PASSWORD 'http://localhost:8080/api/admin/rag/search?q=잔여세대 신청 조건'
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
public class AdminRagController {

    private final AnnouncementIndexer indexer;
    private final VectorSearch vectorSearch;
    private final AnnouncementDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    /** 원문은 있으나 인덱스가 없는(또는 낡은) 공고를 임베딩·저장한다. */
    @PostMapping("/reindex")
    public RagIndexReport reindex() {
        log.info("수동 RAG 인덱싱 요청 수신");
        return indexer.indexPending();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "embeddingAvailable", indexer.isAvailable(),
                "documents", documentRepository.count(),
                "chunks", chunkRepository.count());
    }

    /** 임시 점검용 — 의미 검색이 도는지 확인. Q&A 화면은 다음 슬라이스. */
    @GetMapping("/search")
    public List<VectorSearch.Hit> search(@RequestParam("q") String query,
                                         @RequestParam(value = "k", defaultValue = "5") int topK) {
        return vectorSearch.search(query, topK);
    }
}
