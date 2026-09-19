package com.contestmate.contest;

import javax.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standardized contest record. Populated either by the seed data (local demo) or by
 * ExtractionService + DedupService from collected raw documents. Fields whose value could
 * not be confirmed from the source stay null/UNKNOWN and are surfaced to the client as
 * "확인 필요" rather than guessed.
 */
@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 300)
    private String organizer;

    /** Stored as a comma-separated tag list to keep the JPA mapping simple. */
    @Column(length = 500)
    private String categories;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "eligibility_confirmed", nullable = false)
    private boolean eligibilityConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "individual_or_team", nullable = false, length = 20)
    private IndividualOrTeam individualOrTeam = IndividualOrTeam.UNKNOWN;

    @Column(name = "application_start")
    private LocalDate applicationStart;

    private LocalDate deadline;

    @Column(name = "deadline_confirmed", nullable = false)
    private boolean deadlineConfirmed;

    @Column(name = "prize_description", length = 1000)
    private String prizeDescription;

    @Column(name = "prize_amount_krw")
    private Long prizeAmountKrw;

    @Column(length = 100)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_offline", nullable = false, length = 20)
    private OnlineOffline onlineOffline = OnlineOffline.UNKNOWN;

    @Column(name = "submission_items", columnDefinition = "TEXT")
    private String submissionItems;

    @Column(name = "official_url", length = 2000)
    private String officialUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Confidence confidence = Confidence.LOW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContestStatus status = ContestStatus.NEEDS_REVIEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Contest() {
        // JPA / builder-style construction from ExtractionService drafts
    }

    public Contest(String title, String organizer, String description, List<String> categories,
                   String region, boolean online, IndividualOrTeam individualOrTeam,
                   LocalDate applicationStart, LocalDate deadline, String officialUrl) {
        this.title = title;
        this.organizer = organizer;
        this.description = description;
        setCategoryList(categories);
        this.region = region;
        this.onlineOffline = online ? OnlineOffline.ONLINE : OnlineOffline.OFFLINE;
        this.individualOrTeam = individualOrTeam;
        this.applicationStart = applicationStart;
        this.deadline = deadline;
        this.deadlineConfirmed = deadline != null;
        this.eligibilityConfirmed = true;
        this.officialUrl = officialUrl;
        this.confidence = Confidence.HIGH;
        this.status = ContestStatus.VERIFIED;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public List<String> getCategoryList() {
        if (categories == null || categories.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(categories.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setCategoryList(List<String> values) {
        this.categories = values == null ? "" : String.join(",", values);
    }

    /** true when the contest can still be applied to (or has no confirmed deadline). */
    public boolean isOpenForApplication() {
        return deadline == null || !deadline.isBefore(LocalDate.now());
    }

    public boolean isOnline() {
        return onlineOffline == OnlineOffline.ONLINE || onlineOffline == OnlineOffline.HYBRID;
    }

    // Getters / setters

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEligibility() { return eligibility; }
    public void setEligibility(String eligibility) { this.eligibility = eligibility; }
    public boolean isEligibilityConfirmed() { return eligibilityConfirmed; }
    public void setEligibilityConfirmed(boolean eligibilityConfirmed) { this.eligibilityConfirmed = eligibilityConfirmed; }
    public IndividualOrTeam getIndividualOrTeam() { return individualOrTeam; }
    public void setIndividualOrTeam(IndividualOrTeam individualOrTeam) { this.individualOrTeam = individualOrTeam; }
    public LocalDate getApplicationStart() { return applicationStart; }
    public void setApplicationStart(LocalDate applicationStart) { this.applicationStart = applicationStart; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public boolean isDeadlineConfirmed() { return deadlineConfirmed; }
    public void setDeadlineConfirmed(boolean deadlineConfirmed) { this.deadlineConfirmed = deadlineConfirmed; }
    public String getPrizeDescription() { return prizeDescription; }
    public void setPrizeDescription(String prizeDescription) { this.prizeDescription = prizeDescription; }
    public Long getPrizeAmountKrw() { return prizeAmountKrw; }
    public void setPrizeAmountKrw(Long prizeAmountKrw) { this.prizeAmountKrw = prizeAmountKrw; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public OnlineOffline getOnlineOffline() { return onlineOffline; }
    public void setOnlineOffline(OnlineOffline onlineOffline) { this.onlineOffline = onlineOffline; }
    public String getSubmissionItems() { return submissionItems; }
    public void setSubmissionItems(String submissionItems) { this.submissionItems = submissionItems; }
    public String getOfficialUrl() { return officialUrl; }
    public void setOfficialUrl(String officialUrl) { this.officialUrl = officialUrl; }
    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }
    public ContestStatus getStatus() { return status; }
    public void setStatus(ContestStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
