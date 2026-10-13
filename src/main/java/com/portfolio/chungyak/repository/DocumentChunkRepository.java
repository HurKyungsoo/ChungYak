package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByAnnouncementId(Long announcementId);

    @Transactional
    void deleteByAnnouncementId(Long announcementId);

    long countByAnnouncementId(Long announcementId);

    boolean existsByAnnouncementId(Long announcementId);

    /**
     * 청크 집합의 버전 — 검색 캐시(VectorSearch) 무효화 판단용. 재인덱싱은 지우고 다시 넣으므로(id 증가)
     * 개수나 최대 id 중 하나는 반드시 바뀐다.
     */
    @Query("select count(c) as count, coalesce(max(c.id), 0) as maxId from DocumentChunk c")
    IndexVersion indexVersion();

    interface IndexVersion {
        long getCount();
        long getMaxId();
    }
}
