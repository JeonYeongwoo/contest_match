package com.contestmate.feedback;

import com.contestmate.contest.Contest;

public class FeedbackedContest {
    private final Contest contest;
    private final FeedbackType feedbackType;

    public FeedbackedContest(Contest contest, FeedbackType feedbackType) {
        this.contest = contest;
        this.feedbackType = feedbackType;
    }

    public Contest getContest() { return contest; }
    public FeedbackType getFeedbackType() { return feedbackType; }
}
