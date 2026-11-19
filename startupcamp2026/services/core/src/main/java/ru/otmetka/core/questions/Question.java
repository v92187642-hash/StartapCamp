package ru.otmetka.core.questions;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A question the patient or a relative wants to ask the doctor at the next visit. */
@Entity
@Table(name = "questions")
class Question {

    @Id
    private UUID id;

    private UUID planId;

    private UUID authorId;

    private String text;

    private Instant createdAt;

    private Instant askedAt;

    protected Question() {
        // for JPA
    }

    Question(UUID planId, UUID authorId, String text, Instant now) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.authorId = authorId;
        this.text = text;
        this.createdAt = now;
    }

    void setAskedAt(Instant askedAt) {
        this.askedAt = askedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getPlanId() {
        return planId;
    }

    UUID getAuthorId() {
        return authorId;
    }

    String getText() {
        return text;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getAskedAt() {
        return askedAt;
    }
}
