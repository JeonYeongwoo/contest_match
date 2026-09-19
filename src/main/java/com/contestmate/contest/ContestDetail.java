package com.contestmate.contest;

import java.util.List;

/** Contest plus every original source URL that was merged into it. */
public class ContestDetail {
    private final Contest contest;
    private final List<String> sourceUrls;

    public ContestDetail(Contest contest, List<String> sourceUrls) {
        this.contest = contest;
        this.sourceUrls = sourceUrls;
    }

    public Contest getContest() { return contest; }
    public List<String> getSourceUrls() { return sourceUrls; }
}
