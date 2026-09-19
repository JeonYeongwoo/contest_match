package com.contestmate.collector;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Verbatim capture of one collected item: original URL, source, and fetch time are always
 * kept, regardless of what ExtractionService later manages to structure from `rawContent`.
 */
@Entity
@Table(name = "raw_documents")
public class RawDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_url", nullable = false, length = 2000)
    private String sourceUrl;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "raw_title", length = 1000)
    private String rawTitle;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected RawDocument() {
    }

    public RawDocument(Long sourceId, String sourceUrl, String contentHash, String rawTitle, String rawContent) {
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
        this.contentHash = contentHash;
        this.rawTitle = rawTitle;
        this.rawContent = rawContent;
    }

    public void markProcessed() {
        this.processed = true;
        this.processedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public String getSourceUrl() { return sourceUrl; }
    public String getContentHash() { return contentHash; }
    public String getRawTitle() { return rawTitle; }
    public String getRawContent() { return rawContent; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public boolean isProcessed() { return processed; }
}
