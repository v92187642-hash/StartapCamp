package ru.otmetka.core.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    private UUID id;

    private UUID userId;

    private String tokenHash;

    private Instant expiresAt;

    private Instant createdAt;

    protected RefreshToken() {
        // for JPA
    }

    RefreshToken(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
