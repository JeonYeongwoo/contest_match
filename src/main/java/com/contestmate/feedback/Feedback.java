package com.contestmate.feedback;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Latest feedback status a user has given for one contest. One row per (user, contest) --
 * a new feedback call overwrites the previous status rather than accumulating a history,
 * since only the current status ("saved", "applied", ...) matters for recommendation ranking.
 */
@Entity
@Table(name = "feedback", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "contest_id"}))
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 20)
    private FeedbackType feedbackType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Feedback() {
    }

    public Feedback(Long userId, Long contestId, FeedbackType feedbackType) {
        this.userId = userId;
        this.contestId = contestId;
        this.feedbackType = feedbackType;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getContestId() { return contestId; }
    public FeedbackType getFeedbackType() { return feedbackType; }
    public void setFeedbackType(FeedbackType feedbackType) {
        this.feedbackType = feedbackType;
        this.createdAt = LocalDateTime.now();
    }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
