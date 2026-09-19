package com.contestmate.recommendation;

import com.contestmate.contest.Contest;
import com.contestmate.feedback.FeedbackType;

import java.util.List;

public class Recommendation {
    private final Contest contest;
    private final int score;
    private final List<String> matchedInterests;
    /** 2-3 sentence, deterministically generated explanation grounded in the stored fields. */
    private final String reason;
    private final boolean eligibilityWarning;
    private final String eligibilityWarningText;
    private final List<String> sourceUrls;
    private final FeedbackType userFeedback;

    public Recommendation(Contest contest, int score, List<String> matchedInterests, String reason,
                           boolean eligibilityWarning, String eligibilityWarningText,
                           List<String> sourceUrls, FeedbackType userFeedback) {
        this.contest = contest;
        this.score = score;
        this.matchedInterests = matchedInterests;
        this.reason = reason;
        this.eligibilityWarning = eligibilityWarning;
        this.eligibilityWarningText = eligibilityWarningText;
        this.sourceUrls = sourceUrls;
        this.userFeedback = userFeedback;
    }

    public Contest getContest() { return contest; }
    public int getScore() { return score; }
    public List<String> getMatchedInterests() { return matchedInterests; }
    public String getReason() { return reason; }
    public boolean isEligibilityWarning() { return eligibilityWarning; }
    public String getEligibilityWarningText() { return eligibilityWarningText; }
    public List<String> getSourceUrls() { return sourceUrls; }
    public FeedbackType getUserFeedback() { return userFeedback; }
}
