package com.contestmate.profile;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /extract is a local, no-LLM-required fallback that turns pasted free text (e.g. a ChatGPT
 * conversation export) into a rough interest list -- useful for demos and as a pre-fill for
 * the profile form. /profile persists the actual structured profile used by RecommendationService.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();
    static {
        KEYWORDS.put("AI", Arrays.asList("ai", "artificial intelligence", "machine learning", "llm", "인공지능"));
        KEYWORDS.put("Mobile", Arrays.asList("android", "ios", "kotlin", "mobile", "모바일"));
        KEYWORDS.put("Development", Arrays.asList("development", "programming", "spring", "backend", "개발"));
        KEYWORDS.put("Data", Arrays.asList("data", "analysis", "python", "sql", "데이터"));
        KEYWORDS.put("Game", Arrays.asList("game", "unity", "unreal", "게임"));
        KEYWORDS.put("Design", Arrays.asList("design", "ux", "ui", "figma", "디자인"));
        KEYWORDS.put("Startup", Arrays.asList("startup", "창업", "스타트업"));
    }

    private final UserService userService;
    private final UserProfileRepository userProfileRepository;

    public ProfileController(UserService userService, UserProfileRepository userProfileRepository) {
        this.userService = userService;
        this.userProfileRepository = userProfileRepository;
    }

    @PostMapping("/extract")
    public InterestProfile extract(@RequestBody Map<String, String> payload) {
        String text = payload.get("conversationText");
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationText is required");
        }
        String normalized = text.toLowerCase();
        List<String> interests = KEYWORDS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(normalized::contains))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return new InterestProfile(interests, "keyword-fallback");
    }

    @PostMapping
    public ProfileRequest saveProfile(@RequestHeader("X-User-Id") String clientId, @RequestBody ProfileRequest request) {
        if (clientId == null || clientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Id header is required");
        }
        AppUser user = userService.getOrCreate(clientId);
        UserProfileEntity entity = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> new UserProfileEntity(user));
        entity.setInterestList(request.getInterests());
        entity.setMajorOrJob(request.getMajorOrJob());
        entity.setRegion(request.getRegion());
        entity.setAge(request.getAge());
        entity.setEligibilityNote(request.getEligibilityNote());
        entity.setTeamPreference(request.getTeamPreference());
        entity.setPrizePreference(request.getPrizePreference());
        entity.setDeadlinePreferenceDays(request.getDeadlinePreferenceDays());
        entity.setOnlinePreference(request.getOnlinePreference());
        userProfileRepository.save(entity);
        return request;
    }

    @GetMapping
    public ProfileRequest getProfile(@RequestHeader("X-User-Id") String clientId) {
        AppUser user = userService.getOrCreate(clientId);
        UserProfileEntity entity = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not set yet"));
        ProfileRequest response = new ProfileRequest();
        response.setInterests(entity.getInterestList());
        response.setMajorOrJob(entity.getMajorOrJob());
        response.setRegion(entity.getRegion());
        response.setAge(entity.getAge());
        response.setEligibilityNote(entity.getEligibilityNote());
        response.setTeamPreference(entity.getTeamPreference());
        response.setPrizePreference(entity.getPrizePreference());
        response.setDeadlinePreferenceDays(entity.getDeadlinePreferenceDays());
        response.setOnlinePreference(entity.getOnlinePreference());
        return response;
    }
}
