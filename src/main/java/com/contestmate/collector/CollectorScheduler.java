package com.contestmate.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "contest-mate.collector.enabled", havingValue = "true", matchIfMissing = true)
public class CollectorScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectorScheduler.class);
    private final CollectorRunner collectorRunner;

    public CollectorScheduler(CollectorRunner collectorRunner) {
        this.collectorRunner = collectorRunner;
    }

    @Scheduled(cron = "${contest-mate.collector.cron:0 */30 * * * *}")
    public void scheduledRun() {
        log.info("Running scheduled contest collection pass");
        collectorRunner.runAll();
    }
}
