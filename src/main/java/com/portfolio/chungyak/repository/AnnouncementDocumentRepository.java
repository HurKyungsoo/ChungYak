package com.portfolio.chungyak.repository;

import com.portfolio.chungyak.domain.AnnouncementDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnouncementDocumentRepository extends JpaRepository<AnnouncementDocument, Long> {

    Optional<AnnouncementDocument> findByAnnouncementId(Long announcementId);

    boolean existsByAnnouncementId(Long announcementId);
}
