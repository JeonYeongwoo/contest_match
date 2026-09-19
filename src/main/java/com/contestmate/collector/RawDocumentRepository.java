package com.contestmate.collector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RawDocumentRepository extends JpaRepository<RawDocument, Long> {
    List<RawDocument> findByProcessedFalse();
    Optional<RawDocument> findBySourceIdAndContentHash(Long sourceId, String contentHash);
}
