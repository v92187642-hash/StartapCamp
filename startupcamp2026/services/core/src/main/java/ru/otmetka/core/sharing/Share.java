package ru.otmetka.core.sharing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shares")
class Share {

    static final String PENDING = "pending";
    static final String ACCEPTED = "accepted";

    @Id
    private UUID id;

    private UUID planId;

    private String invitedEmail;

    private UUID memberId;

    private String role;

    private String status;

    private String tokenHash;

    private Instant expiresAt;

    private Instant createdAt;

    protected Share() {
        // for JPA
    }

    Share(UUID planId, String invitedEmail, Instant now) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.invitedEmail = invitedEmail;
        this.role = "viewer";
        this.status = PENDING;
        this.createdAt = now;
    }

    /** A new link replaces the previous one. */
    void renewToken(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** The token is used up: the link works once. */
    void accept(UUID memberId) {
        this.memberId = memberId;
        this.status = ACCEPTED;
        this.tokenHash = null;
    }

    boolean isPending() {
        return PENDING.equals(status);
    }

    UUID getId() {
        return id;
    }

    UUID getPlanId() {
        return planId;
    }

    String getInvitedEmail() {
        return invitedEmail;
    }

    UUID getMemberId() {
        return memberId;
    }

    String getRole() {
        return role;
    }

    String getStatus() {
        return status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }
}
