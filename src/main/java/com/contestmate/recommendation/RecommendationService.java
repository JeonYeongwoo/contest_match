package com.contestmate.recommendation;

import com.contestmate.contest.Contest;
import com.contestmate.contest.ContestRepository;
import com.contestmate.contest.ContestSourceRepository;
import com.contestmate.contest.IndividualOrTeam;
import com.contestmate.feedback.Feedback;
import com.contestmate.feedback.FeedbackRepository;
import com.contestmate.feedback.FeedbackType;
import com.contestmate.profile.OnlinePreference;
import com.contestmate.profile.PrizePreference;
import com.contestmate.profile.TeamPreference;
import com.contestmate.profile.UserProfileEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic 0-100 scoring across six weighted factors (interest fit, eligibility,
 * deadline proximity, prize preference, region/mode, individual-or-team). Kept
 * deterministic/explainable rather than LLM-scored so every point in the score traces back
 * to a stored field, and so recommendations still work with zero LLM/GPU availability.
 */
@Service
public class RecommendationService {

    private static final int WEIGHT_INTEREST = 40;
    private static final int WEIGHT_ELIGIBILITY = 10;
    private static final int WEIGHT_DEADLINE = 15;
    private static final int WEIGHT_PRIZE = 15;
    private static final int WEIGHT_REGION_MODE = 10;
    private static final int WEIGHT_TEAM = 10;
    private static final int DEFAULT_DEADLINE_WINDOW_DAYS = 30;

    private final ContestRepository contestRepository;
    private final ContestSourceRepository contestSourceRepository;
    private final FeedbackRepository feedbackRepository;

    public RecommendationService(ContestRepository contestRepository,
                                  ContestSourceRepository contestSourceRepository,
                                  FeedbackRepository feedbackRepository) {
        this.contestRepository = contestRepository;
        this.contestSourceRepository = contestSourceRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /** Merges a saved profile (nullable, e.g. guest) with per-request overrides. */
    public EffectiveProfile resolveProfile(UserProfileEntity saved, RecommendationRequest request) {
        EffectiveProfile p = new EffectiveProfile();
        p.interests = !request.getInterests().isEmpty() ? request.getInterests()
                : (saved != null ? saved.getInterestList() : new ArrayList<>());
        p.region = request.getRegion() != null ? request.getRegion() : (saved != null ? saved.getRegion() : null);
        p.teamPreference = request.getTeamPreference() != null ? request.getTeamPreference()
                : (saved != null ? saved.getTeamPreference() : TeamPreference.ANY);
        p.prizePreference = request.getPrizePreference() != null ? request.getPrizePreference()
                : (saved != null ? saved.getPrizePreference() : PrizePreference.ANY);
        p.onlinePreference = request.getOnlinePreference() != null ? request.getOnlinePreference()
                : (saved != null ? saved.getOnlinePreference() : OnlinePreference.ANY);
        p.deadlinePreferenceDays = request.getDeadlinePreferenceDays() != null ? request.getDeadlinePreferenceDays()
                : (saved != null ? saved.getDeadlinePreferenceDays() : null);
        p.majorOrJob = saved != null ? saved.getMajorOrJob() : null;
        p.eligibilityNote = saved != null ? saved.getEligibilityNote() : null;
        return p;
    }

    public List<Recommendation> recommend(EffectiveProfile profile, Long userId, int limit) {
        // HashMap (not Map.of()) because contest.getId() can be looked up here, and an
        // immutable Map throws NPE on get(null) even when checking for absence.
        Map<Long, FeedbackType> feedbackByContest = new HashMap<>();
        if (userId != null) {
            feedbackRepository.findByUserId(userId)
                    .forEach(f -> feedbackByContest.put(f.getContestId(), f.getFeedbackType()));
        }

        return contestRepository.findAll().stream()
                .filter(Contest::isOpenForApplication)
                .filter(c -> feedbackByContest.get(c.getId()) != FeedbackType.NOT_INTERESTED)
                .filter(c -> feedbackByContest.get(c.getId()) != FeedbackType.APPLIED)
                .map(c -> score(c, profile, feedbackByContest.get(c.getId())))
                .sorted(Comparator.comparingInt(Recommendation::getScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Recommendation score(Contest contest, EffectiveProfile profile, FeedbackType feedback) {
        List<String> matchedInterests = profile.interests.stream()
                .filter(interest -> contest.getCategoryList().stream()
                        .anyMatch(category -> category.equalsIgnoreCase(interest)))
                .collect(Collectors.toList());
        int interestScore = profile.interests.isEmpty() ? WEIGHT_INTEREST / 2
                : (int) Math.round(WEIGHT_INTEREST * Math.min(1.0, matchedInterests.size() / (double) profile.interests.size()));

        boolean eligibilityWarning = !contest.isEligibilityConfirmed();
        String eligibilityWarningText = eligibilityWarning
                ? "지원 자격이 공고 원문에서 명확히 확인되지 않았습니다 (확인 필요). 지원 전 원문을 꼭 확인하세요."
                : null;
        int eligibilityScore = !eligibilityWarning ? WEIGHT_ELIGIBILITY : WEIGHT_ELIGIBILITY / 2;

        int deadlineScore = scoreDeadline(contest, profile);
        int prizeScore = scorePrize(contest, profile);
        int regionModeScore = scoreRegionMode(contest, profile);
        int teamScore = scoreTeam(contest, profile);

        int total = Math.min(100, interestScore + eligibilityScore + deadlineScore + prizeScore + regionModeScore + teamScore);

        List<String> sourceUrls = contestSourceRepository.findByContestId(contest.getId()).stream()
                .map(cs -> cs.getSourceUrl())
                .distinct()
                .collect(Collectors.toList());
        if (sourceUrls.isEmpty() && contest.getOfficialUrl() != null) {
            sourceUrls = List.of(contest.getOfficialUrl());
        }

        String reason = buildReason(contest, profile, matchedInterests, deadlineScore, prizeScore, regionModeScore, teamScore);
        return new Recommendation(contest, total, matchedInterests, reason, eligibilityWarning, eligibilityWarningText,
                sourceUrls, feedback);
    }

    private int scoreDeadline(Contest contest, EffectiveProfile profile) {
        if (contest.getDeadline() == null) return WEIGHT_DEADLINE / 2; // unknown deadline: neutral
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), contest.getDeadline());
        if (daysLeft < 0) return 0;
        int window = profile.deadlinePreferenceDays != null ? profile.deadlinePreferenceDays : DEFAULT_DEADLINE_WINDOW_DAYS;
        if (daysLeft <= window) {
            // Closer to the deadline (but still open) scores higher, within the preferred window.
            return (int) Math.round(WEIGHT_DEADLINE * (1.0 - (daysLeft / (double) Math.max(window, 1)) * 0.5));
        }
        // Beyond the preferred window: decays gradually rather than dropping to zero.
        double overshoot = Math.min(1.0, (daysLeft - window) / (double) (window * 2));
        return (int) Math.round(WEIGHT_DEADLINE * 0.5 * (1.0 - overshoot));
    }

    private int scorePrize(Contest contest, EffectiveProfile profile) {
        if (profile.prizePreference == null || profile.prizePreference == PrizePreference.ANY) return WEIGHT_PRIZE / 2 + WEIGHT_PRIZE / 4;
        if (contest.getPrizeAmountKrw() == null) return WEIGHT_PRIZE / 3; // unknown: mild neutral score
        long amount = contest.getPrizeAmountKrw();
        PrizePreference bucket = amount < 1_000_000 ? PrizePreference.LOW
                : amount <= 5_000_000 ? PrizePreference.MEDIUM
                : PrizePreference.HIGH;
        return bucket == profile.prizePreference ? WEIGHT_PRIZE : WEIGHT_PRIZE / 4;
    }

    private int scoreRegionMode(Contest contest, EffectiveProfile profile) {
        boolean onlineOk = profile.onlinePreference == null || profile.onlinePreference == OnlinePreference.ANY
                || (profile.onlinePreference == OnlinePreference.ONLINE && contest.isOnline())
                || (profile.onlinePreference == OnlinePreference.OFFLINE && !contest.isOnline());
        boolean regionOk = profile.region == null || profile.region.isBlank() || contest.isOnline()
                || (contest.getRegion() != null && contest.getRegion().equalsIgnoreCase(profile.region));
        if (onlineOk && regionOk) return WEIGHT_REGION_MODE;
        if (onlineOk || regionOk) return WEIGHT_REGION_MODE / 2;
        return 0;
    }

    private int scoreTeam(Contest contest, EffectiveProfile profile) {
        if (profile.teamPreference == null || profile.teamPreference == TeamPreference.ANY) return WEIGHT_TEAM;
        if (contest.getIndividualOrTeam() == IndividualOrTeam.UNKNOWN) return WEIGHT_TEAM / 2;
        if (contest.getIndividualOrTeam() == IndividualOrTeam.BOTH) return WEIGHT_TEAM;
        boolean matches = (profile.teamPreference == TeamPreference.TEAM && contest.getIndividualOrTeam() == IndividualOrTeam.TEAM)
                || (profile.teamPreference == TeamPreference.INDIVIDUAL && contest.getIndividualOrTeam() == IndividualOrTeam.INDIVIDUAL);
        return matches ? WEIGHT_TEAM : 0;
    }

    private String buildReason(Contest contest, EffectiveProfile profile, List<String> matchedInterests,
                                int deadlineScore, int prizeScore, int regionModeScore, int teamScore) {
        List<String> parts = new ArrayList<>();
        if (!matchedInterests.isEmpty()) {
            parts.add("관심 분야(" + String.join(", ", matchedInterests) + ")와 일치합니다.");
        }
        if (contest.getDeadline() != null && deadlineScore >= WEIGHT_DEADLINE / 2) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), contest.getDeadline());
            parts.add("마감까지 " + daysLeft + "일 남아 지원 여유가 " + (daysLeft <= 7 ? "얼마 없습니다." : "있습니다."));
        } else if (contest.getDeadline() == null) {
            parts.add("마감일이 원문에서 확인되지 않아 별도 확인이 필요합니다.");
        }
        if (prizeScore >= WEIGHT_PRIZE * 0.75) {
            parts.add("선호하신 상금/혜택 수준과 맞습니다.");
        }
        if (regionModeScore >= WEIGHT_REGION_MODE * 0.75) {
            parts.add(contest.isOnline() ? "온라인으로 참여 가능합니다." : "선호 지역(" + profile.region + ")과 맞는 개최지입니다.");
        }
        if (teamScore >= WEIGHT_TEAM * 0.75 && profile.teamPreference != null && profile.teamPreference != TeamPreference.ANY) {
            parts.add("선호하신 " + (profile.teamPreference == TeamPreference.TEAM ? "팀" : "개인") + " 참가 조건과 일치합니다.");
        }
        if (parts.isEmpty()) {
            parts.add("설정하신 조건과 부분적으로 일치하는 공모전입니다. 상세 내용을 확인해 보세요.");
        }
        while (parts.size() > 3) parts.remove(parts.size() - 1);
        return String.join(" ", parts);
    }

    /** Profile actually used for scoring, after merging saved profile + request overrides. */
    public static class EffectiveProfile {
        public List<String> interests = new ArrayList<>();
        public String region;
        public TeamPreference teamPreference = TeamPreference.ANY;
        public PrizePreference prizePreference = PrizePreference.ANY;
        public OnlinePreference onlinePreference = OnlinePreference.ANY;
        public Integer deadlinePreferenceDays;
        public String majorOrJob;
        public String eligibilityNote;
    }
}
