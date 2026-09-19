package com.contestmate.contest;

import com.contestmate.collector.Source;
import com.contestmate.collector.SourceRepository;
import com.contestmate.collector.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * Local-demo seed data so the app is usable immediately after `docker compose up` without
 * waiting for the scheduler's first collection pass. Disable with SEED_ENABLED=false in a
 * real deployment once real sources are collecting data.
 */
@Component
public class ContestSeeder implements CommandLineRunner {

    private final ContestRepository contestRepository;
    private final SourceRepository sourceRepository;
    private final boolean enabled;

    public ContestSeeder(ContestRepository contestRepository, SourceRepository sourceRepository,
                          @Value("${contest-mate.seed.enabled:true}") boolean enabled) {
        this.contestRepository = contestRepository;
        this.sourceRepository = sourceRepository;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled || contestRepository.count() > 0) {
            return;
        }

        contestRepository.saveAll(Arrays.asList(
                new Contest("2026 Seoul AI Idea Contest", "Seoul AI Hub",
                        "Propose an AI service that solves a city problem.",
                        Arrays.asList("AI", "Data", "Startup"), "Seoul", false, IndividualOrTeam.TEAM,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 15), "https://example.com/ai-idea"),
                new Contest("National Student App Challenge", "Student Developer Network",
                        "Online hackathon covering mobile app planning and delivery.",
                        Arrays.asList("Mobile", "Development", "Hackathon"), "Online", true, IndividualOrTeam.TEAM,
                        LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5), "https://example.com/app-challenge"),
                new Contest("Healthcare Data Analysis Contest", "Health Data Center",
                        "Analyze public data and propose a health solution.",
                        Arrays.asList("Data", "AI"), "Daejeon", false, IndividualOrTeam.INDIVIDUAL,
                        LocalDate.of(2026, 9, 10), LocalDate.of(2026, 11, 1), "https://example.com/health-data"),
                new Contest("Game Content Planning Contest", "Game Culture Foundation",
                        "Submit a creative game-content project proposal.",
                        Arrays.asList("Design", "Startup"), "Busan", false, IndividualOrTeam.BOTH,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 25), "https://example.com/game"),
                new Contest("Green Campus Project", "Green Campus Network",
                        "Find a practical idea for sustainable campus life.",
                        Arrays.asList("Startup", "Design"), "Online", true, IndividualOrTeam.BOTH,
                        LocalDate.of(2026, 9, 15), LocalDate.of(2026, 11, 15), "https://example.com/green")
        ));

        if (sourceRepository.count() == 0) {
            // Demo RSS source: replace with a real contest-portal RSS feed the operator has confirmed
            // is allowed by its robots.txt/terms before enabling the scheduler against it.
            Source placeholder = new Source(
                    "Example Public-Sector Contest RSS (replace before production use)",
                    "https://example.com/contests/rss",
                    SourceType.RSS,
                    "Placeholder demo source -- verify robots.txt and terms of use before enabling.");
            placeholder.setEnabled(false); // disabled: not a real feed, registered only to show the admin UI shape
            sourceRepository.save(placeholder);
        }
    }
}
