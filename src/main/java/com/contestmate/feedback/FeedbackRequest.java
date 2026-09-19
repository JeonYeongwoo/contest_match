package com.contestmate.feedback;

public class FeedbackRequest {
    private Long contestId;
    private FeedbackType feedbackType;

    public Long getContestId() { return contestId; }
    public void setContestId(Long contestId) { this.contestId = contestId; }
    public FeedbackType getFeedbackType() { return feedbackType; }
    public void setFeedbackType(FeedbackType feedbackType) { this.feedbackType = feedbackType; }
}
