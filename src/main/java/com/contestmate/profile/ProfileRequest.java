package com.contestmate.profile;

import java.util.ArrayList;
import java.util.List;

/** Body for POST /api/profile -- persists the user's stated interests/eligibility/preferences. */
public class ProfileRequest {
    private List<String> interests = new ArrayList<>();
    private String majorOrJob;
    private String region;
    private Integer age;
    private String eligibilityNote;
    private TeamPreference teamPreference = TeamPreference.ANY;
    private PrizePreference prizePreference = PrizePreference.ANY;
    private Integer deadlinePreferenceDays;
    private OnlinePreference onlinePreference = OnlinePreference.ANY;

    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
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
