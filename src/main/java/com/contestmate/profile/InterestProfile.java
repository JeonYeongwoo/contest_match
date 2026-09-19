package com.contestmate.profile;

import java.util.List;

public class InterestProfile {
    private final List<String> interests;
    private final String source;

    public InterestProfile(List<String> interests, String source) {
        this.interests = interests;
        this.source = source;
    }

    public List<String> getInterests() { return interests; }
    public String getSource() { return source; }
}
