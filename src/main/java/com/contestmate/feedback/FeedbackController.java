package com.contestmate.feedback;

import com.contestmate.contest.Contest;
import com.contestmate.contest.ContestRepository;
import com.contestmate.profile.AppUser;
import com.contestmate.profile.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;
    private final UserService userService;
    private final ContestRepository contestRepository;

    public FeedbackController(FeedbackRepository feedbackRepository, UserService userService,
                               ContestRepository contestRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userService = userService;
        this.contestRepository = contestRepository;
    }

    @GetMapping
    public List<Feedback> list(@RequestHeader("X-User-Id") String clientId) {
        AppUser user = userService.getOrCreate(clientId);
        return feedbackRepository.findByUserId(user.getId());
    }

    /** Convenience for the "저장한 공모전" screen: feedback joined with the contest it points to. */
    @GetMapping("/contests")
    public List<FeedbackedContest> listWithContests(@RequestHeader("X-User-Id") String clientId) {
        AppUser user = userService.getOrCreate(clientId);
        return feedbackRepository.findByUserId(user.getId()).stream()
                .map(f -> contestRepository.findById(f.getContestId())
                        .map(c -> new FeedbackedContest(c, f.getFeedbackType()))
                        .orElse(null))
                .filter(fc -> fc != null)
                .collect(Collectors.toList());
    }

    @PostMapping
    @Transactional
    public Feedback submit(@RequestHeader("X-User-Id") String clientId, @RequestBody FeedbackRequest request) {
        if (request.getContestId() == null || request.getFeedbackType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contestId and feedbackType are required");
        }
        AppUser user = userService.getOrCreate(clientId);
        Feedback feedback = feedbackRepository.findByUserIdAndContestId(user.getId(), request.getContestId())
                .orElseGet(() -> new Feedback(user.getId(), request.getContestId(), request.getFeedbackType()));
        feedback.setFeedbackType(request.getFeedbackType());
        return feedbackRepository.save(feedback);
    }
}
