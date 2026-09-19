package com.contestmate.bookmark;

import com.contestmate.contest.Contest;
import com.contestmate.contest.ContestRepository;
import com.contestmate.feedback.Feedback;
import com.contestmate.feedback.FeedbackRepository;
import com.contestmate.feedback.FeedbackType;
import com.contestmate.profile.AppUser;
import com.contestmate.profile.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thin convenience wrapper kept for backward compatibility with the original MVP API shape:
 * a "bookmark" is simply feedback of type SAVED. New clients should prefer POST /api/feedback
 * directly, since it also supports NOT_INTERESTED / PLAN_TO_APPLY / APPLIED.
 */
@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private static final String ANONYMOUS_CLIENT_ID = "anonymous-demo-user";

    private final ContestRepository contestRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserService userService;

    public BookmarkController(ContestRepository contestRepository, FeedbackRepository feedbackRepository, UserService userService) {
        this.contestRepository = contestRepository;
        this.feedbackRepository = feedbackRepository;
        this.userService = userService;
    }

    @GetMapping
    public List<Contest> list(@RequestHeader(value = "X-User-Id", required = false) String clientId) {
        AppUser user = userService.getOrCreate(resolveClientId(clientId));
        return feedbackRepository.findByUserId(user.getId()).stream()
                .filter(f -> f.getFeedbackType() == FeedbackType.SAVED)
                .map(f -> contestRepository.findById(f.getContestId()).orElse(null))
                .filter(c -> c != null)
                .collect(Collectors.toList());
    }

    @PostMapping("/{contestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void add(@PathVariable Long contestId, @RequestHeader(value = "X-User-Id", required = false) String clientId) {
        if (!contestRepository.existsById(contestId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found");
        }
        AppUser user = userService.getOrCreate(resolveClientId(clientId));
        Feedback feedback = feedbackRepository.findByUserIdAndContestId(user.getId(), contestId)
                .orElseGet(() -> new Feedback(user.getId(), contestId, FeedbackType.SAVED));
        feedback.setFeedbackType(FeedbackType.SAVED);
        feedbackRepository.save(feedback);
    }

    @DeleteMapping("/{contestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long contestId, @RequestHeader(value = "X-User-Id", required = false) String clientId) {
        AppUser user = userService.getOrCreate(resolveClientId(clientId));
        feedbackRepository.findByUserIdAndContestId(user.getId(), contestId)
                .filter(f -> f.getFeedbackType() == FeedbackType.SAVED)
                .ifPresent(feedbackRepository::delete);
    }

    private String resolveClientId(String clientId) {
        return clientId == null || clientId.isBlank() ? ANONYMOUS_CLIENT_ID : clientId;
    }
}
