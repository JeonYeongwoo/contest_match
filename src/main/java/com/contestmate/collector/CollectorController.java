package com.contestmate.collector;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin/ops surface for managing sources and manually triggering a collection pass --
 * useful for demos since the scheduled cron run may not have fired yet.
 */
@RestController
@RequestMapping("/api/admin")
public class CollectorController {

    private final SourceRepository sourceRepository;
    private final CollectorRunner collectorRunner;
    private final RobotsTxtChecker robotsTxtChecker;
    private final RawDocumentRepository rawDocumentRepository;

    public CollectorController(SourceRepository sourceRepository, CollectorRunner collectorRunner,
                                RobotsTxtChecker robotsTxtChecker, RawDocumentRepository rawDocumentRepository) {
        this.sourceRepository = sourceRepository;
        this.collectorRunner = collectorRunner;
        this.robotsTxtChecker = robotsTxtChecker;
        this.rawDocumentRepository = rawDocumentRepository;
    }

    @GetMapping("/sources")
    public List<Source> listSources() {
        return sourceRepository.findAll();
    }

    @PostMapping("/sources")
    public Source registerSource(@RequestBody SourceRequest request) {
        if (request.getName() == null || request.getBaseUrl() == null || request.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name, baseUrl and type are required");
        }
        Source source = new Source(request.getName(), request.getBaseUrl(), request.getType(), request.getTermsNote());
        source.setRobotsAllowed(robotsTxtChecker.isAllowed(request.getBaseUrl()));
        source.setRobotsCheckedAt(LocalDateTime.now());
        if (Boolean.FALSE.equals(source.getRobotsAllowed())) {
            source.setEnabled(false); // registered for visibility, but never collected from
        }
        return sourceRepository.save(source);
    }

    @PostMapping("/collectors/run")
    public String runAll() {
        collectorRunner.runAll();
        return "collection pass triggered";
    }

    @PostMapping("/collectors/run/{sourceId}")
    public String runOne(@PathVariable Long sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source not found"));
        collectorRunner.runOne(source);
        return "collection pass triggered for " + source.getName();
    }

    @GetMapping("/raw-documents/pending-count")
    public long pendingCount() {
        return rawDocumentRepository.findByProcessedFalse().size();
    }
}
