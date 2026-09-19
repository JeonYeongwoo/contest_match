package com.contestmate.collector;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "base_url", nullable = false, length = 1000)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType type;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "robots_allowed")
    private Boolean robotsAllowed;

    @Column(name = "robots_checked_at")
    private LocalDateTime robotsCheckedAt;

    @Column(name = "terms_note", length = 1000)
    private String termsNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Source() {
    }

    public Source(String name, String baseUrl, SourceType type, String termsNote) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.type = type;
        this.termsNote = termsNote;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public SourceType getType() { return type; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Boolean getRobotsAllowed() { return robotsAllowed; }
    public void setRobotsAllowed(Boolean robotsAllowed) { this.robotsAllowed = robotsAllowed; }
    public LocalDateTime getRobotsCheckedAt() { return robotsCheckedAt; }
    public void setRobotsCheckedAt(LocalDateTime robotsCheckedAt) { this.robotsCheckedAt = robotsCheckedAt; }
    public String getTermsNote() { return termsNote; }
}
