package com.contestmate.collector;

public class SourceRequest {
    private String name;
    private String baseUrl;
    private SourceType type;
    private String termsNote;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public SourceType getType() { return type; }
    public void setType(SourceType type) { this.type = type; }
    public String getTermsNote() { return termsNote; }
    public void setTermsNote(String termsNote) { this.termsNote = termsNote; }
}
