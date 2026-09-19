package com.contestmate.profile;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Minimal anonymous user record: only a client-generated id (e.g. a UUID kept in
 * localStorage by the web app), no email/name/PII. Identifies feedback and saved profile.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AppUser() {
    }

    public AppUser(String clientId) {
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public String getClientId() { return clientId; }
}
