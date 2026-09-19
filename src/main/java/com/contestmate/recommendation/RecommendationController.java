package com.contestmate.recommendation;

import com.contestmate.contest.Contest;
import com.contestmate.contest.ContestRepository;
import com.contestmate.profile.AppUser;
import com.contestmate.profile.UserProfileEntity;
import com.contestmate.profile.UserProfileRepository;
import com.contestmate.profile.UserService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final UserService userService;
    private final UserProfileRepository userProfileRepository;
    private final ContestRepository contestRepository;

    public RecommendationController(RecommendationService recommendationService, UserService userService,
                                     UserProfileRepository userProfileRepository, ContestRepository contestRepository) {
        this.recommendationService = recommendationService;
        this.userService = userService;
        this.userProfileRepository = userProfileRepository;
        this.contestRepository = contestRepository;
    }

    @PostMapping
    public List<Recommendation> recommend(@RequestHeader(value = "X-User-Id", required = false) String clientId,
                                           @RequestBody(required = false) RecommendationRequest request) {
        RecommendationRequest effectiveRequest = request != null ? request : new RecommendationRequest();
        Long userId = null;
        UserProfileEntity saved = null;
        if (clientId != null && !clientId.isBlank()) {
            AppUser user = userService.getOrCreate(clientId);
            userId = user.getId();
            saved = userProfileRepository.findByUserId(userId).orElse(null);
        }
        RecommendationService.EffectiveProfile profile = recommendationService.resolveProfile(saved, effectiveRequest);
        return recommendationService.recommend(profile, userId, effectiveRequest.getLimit());
    }

    /** 홈 화면: 오늘의 추천 / 마감 임박 / 인기 분야. */
    @GetMapping("/home")
    public HomeResponse home(@RequestHeader(value = "X-User-Id", required = false) String clientId) {
        Long userId = null;
        UserProfileEntity saved = null;
        if (clientId != null && !clientId.isBlank()) {
            AppUser user = userService.getOrCreate(clientId);
            userId = user.getId();
            saved = userProfileRepository.findByUserId(userId).orElse(null);
        }
        RecommendationService.EffectiveProfile profile = recommendationService.resolveProfile(saved, new RecommendationRequest());
        List<Recommendation> todaysPicks = recommendationService.recommend(profile, userId, 4);

        List<Contest> deadlineSoon = contestRepository
                .findTop20ByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate.now())
                .stream().limit(5).collect(Collectors.toList());

        Map<String, Long> categoryCounts = contestRepository.findAll().stream()
                .flatMap(c -> c.getCategoryList().stream())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        List<String> popularCategories = categoryCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        return new HomeResponse(todaysPicks, deadlineSoon, popularCategories);
    }

    public static class HomeResponse {
        private final List<Recommendation> todaysPicks;
        private final List<Contest> deadlineSoon;
        private final List<String> popularCategories;

        public HomeResponse(List<Recommendation> todaysPicks, List<Contest> deadlineSoon, List<String> popularCategories) {
            this.todaysPicks = todaysPicks;
            this.deadlineSoon = deadlineSoon;
            this.popularCategories = popularCategories;
        }

        public List<Recommendation> getTodaysPicks() { return todaysPicks; }
        public List<Contest> getDeadlineSoon() { return deadlineSoon; }
        public List<String> getPopularCategories() { return popularCategories; }
    }
}
