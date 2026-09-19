package com.contestmate.collector;

import com.contestmate.ai.ContestDraft;
import com.contestmate.ai.DedupService;
import com.contestmate.ai.ExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Orchestrates one collection pass: fetch -> hash/dedupe-by-hash -> persist raw_documents ->
 * extract -> merge into contests. Every step keeps the original source URL and timestamp.
 */
@Service
public class CollectorRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectorRunner.class);

    private final SourceRepository sourceRepository;
    private final RawDocumentRepository rawDocumentRepository;
    private final RobotsTxtChecker robotsTxtChecker;
    private final List<Collector> collectors;
    private final ExtractionService extractionService;
    private final DedupService dedupService;

    public CollectorRunner(SourceRepository sourceRepository, RawDocumentRepository rawDocumentRepository,
                            RobotsTxtChecker robotsTxtChecker, List<Collector> collectors,
                            ExtractionService extractionService, DedupService dedupService) {
        this.sourceRepository = sourceRepository;
        this.rawDocumentRepository = rawDocumentRepository;
        this.robotsTxtChecker = robotsTxtChecker;
        this.collectors = collectors;
        this.extractionService = extractionService;
        this.dedupService = dedupService;
    }

    public void runAll() {
        sourceRepository.findByEnabledTrue().forEach(this::runOne);
    }

    public void runOne(Source source) {
        if (!robotsTxtChecker.isAllowed(source.getBaseUrl())) {
            log.info("Skipping source {} ({}) -- disallowed by robots.txt", source.getName(), source.getBaseUrl());
            source.setRobotsAllowed(false);
            sourceRepository.save(source);
            return;
        }
        source.setRobotsAllowed(true);
        sourceRepository.save(source);

        Collector collector = collectors.stream().filter(c -> c.supports(source.getType())).findFirst().orElse(null);
        if (collector == null) {
            log.warn("No collector implementation registered for source type {}", source.getType());
            return;
        }

        try {
            List<CollectedItem> items = collector.collect(source);
            for (CollectedItem item : items) {
                ingest(source, item);
            }
        } catch (Exception e) {
            log.warn("Collection failed for source {}: {}", source.getName(), e.getMessage());
        }
    }

    @Transactional
    protected void ingest(Source source, CollectedItem item) {
        String hash = sha256(item.getTitle() + "|" + item.getContent());
        if (rawDocumentRepository.findBySourceIdAndContentHash(source.getId(), hash).isPresent()) {
            return; // unchanged since last collection
        }
        RawDocument doc = rawDocumentRepository.save(
                new RawDocument(source.getId(), item.getSourceUrl(), hash, item.getTitle(), item.getContent()));

        ContestDraft draft = extractionService.extract(doc);
        dedupService.mergeOrCreate(draft, source.getId(), doc.getSourceUrl());
        doc.markProcessed();
        rawDocumentRepository.save(doc);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
