package com.contestmate.ai;

import com.contestmate.contest.Confidence;
import com.contestmate.contest.IndividualOrTeam;
import com.contestmate.contest.OnlineOffline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Structured extraction output for one raw document, before dedup/merge into `contests`. */
public class ContestDraft {
    public String title;
    public String organizer;
    public List<String> categories = new ArrayList<>();
    public String description;
    public String eligibility;
    public boolean eligibilityConfirmed;
    public IndividualOrTeam individualOrTeam = IndividualOrTeam.UNKNOWN;
    public LocalDate applicationStart;
    public LocalDate deadline;
    public boolean deadlineConfirmed;
    public String prizeDescription;
    public Long prizeAmountKrw;
    public String region;
    public OnlineOffline onlineOffline = OnlineOffline.UNKNOWN;
    public String submissionItems;
    public String officialUrl;
    public Confidence confidence = Confidence.LOW;
}
