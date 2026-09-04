package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByAnnouncementId(Long announcementId);

    @Transactional
    void deleteByAnnouncementId(Long announcementId);

    long countByAnnouncementId(Long announcementId);
}
