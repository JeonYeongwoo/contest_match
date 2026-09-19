package com.contestmate.contest;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * One raw origin URL that contributed to a (possibly merged) Contest.
 * Kept even after de-duplication so the client can always show every original source.
 */
@Entity
@Table(name = "contest_sources")
public class ContestSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_url", nullable = false, length = 2000)
    private String sourceUrl;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt = LocalDateTime.now();

    protected ContestSource() {
    }

    public ContestSource(Contest contest, Long sourceId, String sourceUrl) {
        this.contest = contest;
        this.sourceId = sourceId;
        this.sourceUrl = sourceUrl;
    }

    public Long getId() { return id; }
    public Contest getContest() { return contest; }
    public Long getSourceId() { return sourceId; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
}
