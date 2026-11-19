package ru.otmetka.core.plans;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "plans")
class Plan {

    @Id
    private UUID id;

    private UUID ownerId;

    private String title;

    private LocalDate startsOn;

    private LocalDate endsOn;

    private String timezone;

    private long version;

    private Instant createdAt;

    protected Plan() {
        // for JPA
    }

    Plan(UUID ownerId, String title, LocalDate startsOn, LocalDate endsOn, ZoneId timezone, Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.title = title;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.timezone = timezone.getId();
        this.version = 1;
        this.createdAt = now;
    }

    /** Every change to the plan, its items or its shares raises the version. */
    void touch() {
        version++;
    }

    PlanRef toRef() {
        return new PlanRef(id, ownerId, title, startsOn, endsOn, ZoneId.of(timezone), version);
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    String getTitle() {
        return title;
    }

    LocalDate getStartsOn() {
        return startsOn;
    }

    LocalDate getEndsOn() {
        return endsOn;
    }

    String getTimezone() {
        return timezone;
    }

    long getVersion() {
        return version;
    }
}
