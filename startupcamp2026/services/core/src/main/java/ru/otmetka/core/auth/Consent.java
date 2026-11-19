package ru.otmetka.core.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consents")
class Consent {

    @Id
    private UUID id;

    private UUID userId;

    private String kind;

    private String textVersion;

    private Instant acceptedAt;

    protected Consent() {
        // for JPA
    }

    Consent(UUID userId, String kind, String textVersion, Instant acceptedAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.kind = kind;
        this.textVersion = textVersion;
        this.acceptedAt = acceptedAt;
    }

    String getKind() {
        return kind;
    }

    String getTextVersion() {
        return textVersion;
    }

    Instant getAcceptedAt() {
        return acceptedAt;
    }
}
