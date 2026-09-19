package com.contestmate.recommendation;

import com.contestmate.contest.Contest;
import com.contestmate.contest.ContestRepository;
import com.contestmate.contest.ContestSourceRepository;
import com.contestmate.contest.IndividualOrTeam;
import com.contestmate.feedback.Feedback;
import com.contestmate.feedback.FeedbackRepository;
import com.contestmate.feedback.FeedbackType;
import com.contestmate.profile.TeamPreference;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Runs without Spring/DB: repositories are mocked, only the scoring math is under test. */
class RecommendationServiceTest {

    private final ContestRepository contestRepository = mock(ContestRepository.class);
    private final ContestSourceRepository contestSourceRepository = mock(ContestSourceRepository.class);
    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final RecommendationService service =
            new RecommendationService(contestRepository, contestSourceRepository, feedbackRepository);

    @Test
    void matchingInterestAndOpenDeadlineScoresHigherThanNoMatch() {
        Contest aiContest = new Contest("AI Contest", "Org", "desc", List.of("AI"),
                "Online", true, IndividualOrTeam.TEAM, LocalDate.now(), LocalDate.now().plusDays(5), "https://x/1");
        Contest gameContest = new Contest("Game Contest", "Org", "desc", List.of("Game"),
                "Online", true, IndividualOrTeam.TEAM, LocalDate.now(), LocalDate.now().plusDays(5), "https://x/2");

        when(contestRepository.findAll()).thenReturn(Arrays.asList(aiContest, gameContest));
        when(contestSourceRepository.findByContestId(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());

        RecommendationService.EffectiveProfile profile = new RecommendationService.EffectiveProfile();
        profile.interests = List.of("AI");
        profile.teamPreference = TeamPreference.TEAM;

        List<Recommendation> results = service.recommend(profile, null, 10);

        assertEquals(2, results.size());
        assertEquals("AI Contest", results.get(0).getContest().getTitle());
        assertTrue(results.get(0).getScore() > results.get(1).getScore());
        assertTrue(results.get(0).getMatchedInterests().contains("AI"));
    }

    @Test
    void notInterestedFeedbackExcludesContestFromResults() {
        Contest contest = new Contest("AI Contest", "Org", "desc", List.of("AI"),
                "Online", true, IndividualOrTeam.TEAM, LocalDate.now(), LocalDate.now().plusDays(5), "https://x/1");
        // id is null on a transient entity; feedback lookup keys by id so we only need the map to contain null -> NOT_INTERESTED
        when(contestRepository.findAll()).thenReturn(List.of(contest));
        when(feedbackRepository.findByUserId(1L)).thenReturn(
                List.of(new Feedback(1L, contest.getId(), FeedbackType.NOT_INTERESTED)));

        RecommendationService.EffectiveProfile profile = new RecommendationService.EffectiveProfile();
        profile.interests = List.of("AI");

        List<Recommendation> results = service.recommend(profile, 1L, 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void unconfirmedEligibilityRaisesWarning() {
        Contest contest = new Contest("AI Contest", "Org", "desc", List.of("AI"),
                "Online", true, IndividualOrTeam.TEAM, LocalDate.now(), LocalDate.now().plusDays(5), "https://x/1");
        contest.setEligibilityConfirmed(false); // simulate a low-confidence, freshly-collected draft
        when(contestRepository.findAll()).thenReturn(List.of(contest));
        when(contestSourceRepository.findByContestId(org.mockito.ArgumentMatchers.any())).thenReturn(Collections.emptyList());

        RecommendationService.EffectiveProfile profile = new RecommendationService.EffectiveProfile();
        profile.interests = List.of("AI");

        Recommendation result = service.recommend(profile, null, 10).get(0);

        assertTrue(result.isEligibilityWarning());
        assertNotNull(result.getEligibilityWarningText());
    }
}
