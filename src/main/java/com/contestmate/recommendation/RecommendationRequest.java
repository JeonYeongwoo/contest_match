package com.contestmate.recommendation;

import com.contestmate.profile.OnlinePreference;
import com.contestmate.profile.PrizePreference;
import com.contestmate.profile.TeamPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Every field is an optional override on top of the saved profile (looked up via X-User-Id).
 * A guest with no saved profile and no X-User-Id header must supply interests at minimum.
 */
public class RecommendationRequest {
    private List<String> interests;
    private String region;
    private TeamPreference teamPreference;
    private PrizePreference prizePreference;
    private OnlinePreference onlinePreference;
    private Integer deadlinePreferenceDays;
    private Integer limit = 4;

    public List<String> getInterests() { return interests == null ? new ArrayList<>() : interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public TeamPreference getTeamPreference() { return teamPreference; }
    public void setTeamPreference(TeamPreference teamPreference) { this.teamPreference = teamPreference; }
    public PrizePreference getPrizePreference() { return prizePreference; }
    public void setPrizePreference(PrizePreference prizePreference) { this.prizePreference = prizePreference; }
    public OnlinePreference getOnlinePreference() { return onlinePreference; }
    public void setOnlinePreference(OnlinePreference onlinePreference) { this.onlinePreference = onlinePreference; }
    public Integer getDeadlinePreferenceDays() { return deadlinePreferenceDays; }
    public void setDeadlinePreferenceDays(Integer deadlinePreferenceDays) { this.deadlinePreferenceDays = deadlinePreferenceDays; }
    public Integer getLimit() { return limit == null ? 4 : limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
