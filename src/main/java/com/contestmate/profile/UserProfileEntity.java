package com.contestmate.profile;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(length = 500)
    private String interests;

    @Column(name = "major_or_job", length = 200)
    private String majorOrJob;

    @Column(length = 100)
    private String region;

    private Integer age;

    @Column(name = "eligibility_note", length = 500)
    private String eligibilityNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_preference", nullable = false, length = 20)
    private TeamPreference teamPreference = TeamPreference.ANY;

    @Enumerated(EnumType.STRING)
    @Column(name = "prize_preference", nullable = false, length = 20)
    private PrizePreference prizePreference = PrizePreference.ANY;

    @Column(name = "deadline_preference_days")
    private Integer deadlinePreferenceDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "online_preference", nullable = false, length = 20)
    private OnlinePreference onlinePreference = OnlinePreference.ANY;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected UserProfileEntity() {
    }

    public UserProfileEntity(AppUser user) {
        this.user = user;
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public List<String> getInterestList() {
        if (interests == null || interests.isBlank()) return new ArrayList<>();
        return Arrays.stream(interests.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public void setInterestList(List<String> values) {
        this.interests = values == null ? "" : String.join(",", values);
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getMajorOrJob() { return majorOrJob; }
    public void setMajorOrJob(String majorOrJob) { this.majorOrJob = majorOrJob; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getEligibilityNote() { return eligibilityNote; }
    public void setEligibilityNote(String eligibilityNote) { this.eligibilityNote = eligibilityNote; }
    public TeamPreference getTeamPreference() { return teamPreference; }
    public void setTeamPreference(TeamPreference teamPreference) { this.teamPreference = teamPreference; }
    public PrizePreference getPrizePreference() { return prizePreference; }
    public void setPrizePreference(PrizePreference prizePreference) { this.prizePreference = prizePreference; }
    public Integer getDeadlinePreferenceDays() { return deadlinePreferenceDays; }
    public void setDeadlinePreferenceDays(Integer deadlinePreferenceDays) { this.deadlinePreferenceDays = deadlinePreferenceDays; }
    public OnlinePreference getOnlinePreference() { return onlinePreference; }
    public void setOnlinePreference(OnlinePreference onlinePreference) { this.onlinePreference = onlinePreference; }
}
