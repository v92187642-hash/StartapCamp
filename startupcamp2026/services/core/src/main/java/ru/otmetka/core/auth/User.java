package ru.otmetka.core.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class User {

    @Id
    private UUID id;

    private String email;

    private String displayName;

    private String passwordHash;

    private boolean premium;

    private Instant createdAt;

    protected User() {
        // for JPA
    }

    User(String email, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = now;
    }

    UUID getId() {
        return id;
    }

    String getEmail() {
        return email;
    }

    /** Null for an account made before passwords existed. */
    String getPasswordHash() {
        return passwordHash;
    }

    boolean isPremium() {
        return premium;
    }

    void setPremium(boolean premium) {
        this.premium = premium;
    }
}
