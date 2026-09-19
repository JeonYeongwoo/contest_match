package com.contestmate.collector;

/** One item fetched from a source, before hashing/persistence/extraction. */
public class CollectedItem {
    private final String sourceUrl;
    private final String title;
    private final String content;

    public CollectedItem(String sourceUrl, String title, String content) {
        this.sourceUrl = sourceUrl;
        this.title = title;
        this.content = content;
    }

    public String getSourceUrl() { return sourceUrl; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
}
