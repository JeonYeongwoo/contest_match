package com.contestmate.contest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contests")
public class ContestController {

    private final ContestRepository contestRepository;
    private final ContestSourceRepository contestSourceRepository;

    public ContestController(ContestRepository contestRepository, ContestSourceRepository contestSourceRepository) {
        this.contestRepository = contestRepository;
        this.contestSourceRepository = contestSourceRepository;
    }

    @GetMapping
    public List<Contest> list(@RequestParam(required = false) String region,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String organizer,
                               @RequestParam(required = false, defaultValue = "false") boolean onlineOnly,
                               @RequestParam(required = false) IndividualOrTeam individualOrTeam,
                               @RequestParam(required = false) Long minPrize,
                               @RequestParam(required = false) Integer deadlineWithinDays,
                               @RequestParam(required = false) String keyword) {
        LocalDate deadlineBefore = deadlineWithinDays == null ? null : LocalDate.now().plusDays(deadlineWithinDays);
        return contestRepository.search(region, category, organizer, onlineOnly, individualOrTeam, minPrize, deadlineBefore, keyword)
                .stream()
                .filter(Contest::isOpenForApplication)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ContestDetail detail(@PathVariable Long id) {
        Contest contest = contestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found"));
        List<String> sourceUrls = contestSourceRepository.findByContestId(id).stream()
                .map(ContestSource::getSourceUrl)
                .distinct()
                .collect(Collectors.toList());
        if (sourceUrls.isEmpty() && contest.getOfficialUrl() != null) {
            sourceUrls = List.of(contest.getOfficialUrl());
        }
        return new ContestDetail(contest, sourceUrls);
    }
}
